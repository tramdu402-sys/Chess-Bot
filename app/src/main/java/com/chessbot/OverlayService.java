package com.chessbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private static final int GREEN = Color.parseColor("#81B64C");
    private static final int AMBER = Color.parseColor("#E5A93C");
    private static final int GRAY = Color.parseColor("#6B7888");
    private static final int RED = Color.parseColor("#E5534B");
    private static final int FROM_RGB = 0x81B64C;
    private static final int TO_RGB = 0x38BDF8;
    private static final int FILL_ALPHA = 90;

    private WindowManager wm;
    private BoardHighlightView boardView;
    private LinearLayout pillView;
    private TextView tvStatus, tvPiece, tvCoords;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BoardScanner scanner = new BoardScanner();

    private int elo = 1500;
    private ChessEngine engine;

    private int boardTop = -1, boardW = 0;
    private int verifyFails = 0, scanTick = 0;
    private boolean synced = false;
    private boolean myWhite = true;
    private int startStable = 0;
    private long candMask = -1, appliedMask = 0;
    private int candCount = 0;
    private int desyncFrames = 0;
    private int thinkGen = 0;
    private long lastTapTime = 0;
    private int hlA = -1, hlB = -1;
    private long hlUntil = 0;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private static class BoardHighlightView extends View {
        private final Paint fromPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint toPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fromFill = new Paint();
        private final Paint toFill = new Paint();
        private int fC = -1, fR = -1, tC = -1, tR = -1;
        private int bTop = 0, sq = 0;
        private boolean active = false;
        private final int[] loc = new int[2];

        BoardHighlightView(Context ctx) {
            super(ctx);
            fromPaint.setStyle(Paint.Style.STROKE);
            fromPaint.setColor(0xFF000000 | FROM_RGB);
            toPaint.setStyle(Paint.Style.STROKE);
            toPaint.setColor(0xFF000000 | TO_RGB);
            fromFill.setStyle(Paint.Style.FILL);
            fromFill.setColor((FILL_ALPHA << 24) | FROM_RGB);
            toFill.setStyle(Paint.Style.FILL);
            toFill.setColor((FILL_ALPHA << 24) | TO_RGB);
        }

        void setMove(int fC, int fR, int tC, int tR, int top, int size) {
            this.fC = fC; this.fR = fR; this.tC = tC; this.tR = tR;
            this.bTop = top; this.sq = size / 8;
            this.active = true;
            postInvalidate();
        }

        void clear() {
            active = false;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!active || sq <= 0) return;
            getLocationOnScreen(loc);
            canvas.save();
            canvas.translate(-loc[0], -loc[1]);
            float stroke = Math.max(3f, sq * 0.05f);
            fromPaint.setStrokeWidth(stroke);
            toPaint.setStrokeWidth(stroke);
            drawCell(canvas, fC, fR, fromFill, fromPaint, stroke);
            drawCell(canvas, tC, tR, toFill, toPaint, stroke);
            canvas.restore();
        }

        private void drawCell(Canvas c, int col, int row, Paint fill, Paint p, float stroke) {
            if (col < 0 || row < 0) return;
            float l = col * sq, t = bTop + row * sq, h = stroke / 2f;
            c.drawRect(l, t, l + sq, t + sq, fill);
            c.drawRect(l + h, t + h, l + sq - h, t + sq - h, p);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int newElo = intent.getIntExtra("ELO", 1500);
            if (engine == null || newElo != elo) {
                elo = newElo;
                engine = new ChessEngine(elo);
                resetGame();
            }
            int code = intent.getIntExtra("RESULT_CODE", -1);
            Intent data = intent.getParcelableExtra("DATA");
            if (code != -1 && data != null && mediaProjection == null) {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = mpm.getMediaProjection(code, data);
                mediaProjection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { handler.post(OverlayService.this::stopSelf); }
                }, handler);
                setupScreenCapture();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, buildNotification());
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        boardView = new BoardHighlightView(this);
        WindowManager.LayoutParams boardParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 30) {
            boardParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            boardParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(boardView, boardParams);

        pillView = new LinearLayout(this);
        pillView.setOrientation(LinearLayout.VERTICAL);
        pillView.setGravity(Gravity.CENTER);
        pillView.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F606080C"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.5f), GREEN);
        pillView.setBackground(bg);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(GRAY);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvStatus.setGravity(Gravity.CENTER);
        pillView.addView(tvStatus);

        tvPiece = new TextView(this);
        tvPiece.setTextColor(Color.WHITE);
        tvPiece.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPiece.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPiece.setGravity(Gravity.CENTER);
        tvPiece.setShadowLayer(5.0f, 0, 0, Color.WHITE);
        tvPiece.setPadding(0, dp(2), 0, dp(2));
        pillView.addView(tvPiece);

        tvCoords = new TextView(this);
        tvCoords.setTextColor(GREEN);
        tvCoords.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvCoords.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCoords.setGravity(Gravity.CENTER);
        pillView.addView(tvCoords);

        final WindowManager.LayoutParams pillParams = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.44f), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        pillParams.gravity = Gravity.TOP | Gravity.END;
        pillParams.x = dp(12);
        pillParams.y = dp(40);

        pillView.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float tX, tY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = pillParams.x; initY = pillParams.y;
                        tX = e.getRawX(); tY = e.getRawY();
                        isDrag = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - tX) > 10 || Math.abs(e.getRawY() - tY) > 10) {
                            isDrag = true;
                            pillParams.x = initX - (int) (e.getRawX() - tX);
                            pillParams.y = initY + (int) (e.getRawY() - tY);
                            wm.updateViewLayout(pillView, pillParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            long now = System.currentTimeMillis();
                            if (now - lastTapTime < 350) stopSelf();
                            else onPillTap();
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(pillView, pillParams);
        setStatus("● ПОИСК ДОСКИ...", GRAY);
        tvPiece.setText("—");
        tvCoords.setText("...");
    }

    private int[] realSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect r = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{r.width(), r.height()};
        }
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    private void setupScreenCapture() {
        int[] size = realSize();
        int density = getResources().getDisplayMetrics().densityDpi;
        imageReader = ImageReader.newInstance(size[0], size[1], PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ChessLiveAI",
                size[0], size[1], density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, handler);
        handler.postDelayed(scanLoop, 300);
    }

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            try { scanScreen(); } catch (Throwable ignored) { }
            handler.postDelayed(this, 250);
        }
    };

    private static final class BufFrame implements BoardScanner.Frame {
        final ByteBuffer buf;
        final int w, h, pStride, rStride;

        BufFrame(ByteBuffer buf, int w, int h, int pStride, int rStride) {
            this.buf = buf; this.w = w; this.h = h; this.pStride = pStride; this.rStride = rStride;
        }

        @Override public int width() { return w; }
        @Override public int height() { return h; }

        @Override
        public int rgb(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) return 0;
            int off = y * rStride + x * pStride;
            if (off + 2 >= buf.limit()) return 0;
            return ((buf.get(off) & 0xFF) << 16) | ((buf.get(off + 1) & 0xFF) << 8) | (buf.get(off + 2) & 0xFF);
        }
    }

    private void scanScreen() {
        if (imageReader == null || engine == null) return;
        Image img = imageReader.acquireLatestImage();
        if (img == null) return;
        try {
            Image.Plane plane = img.getPlanes()[0];
            BufFrame f = new BufFrame(plane.getBuffer(), img.getWidth(), img.getHeight(),
                    plane.getPixelStride(), plane.getRowStride());

            if (boardTop < 0) {
                int top = scanner.detectBoard(f);
                if (top < 0) {
                    setStatus("● ПОИСК ДОСКИ...", GRAY);
                    return;
                }
                boardTop = top;
                boardW = f.w;
                verifyFails = 0;
                resetGame();
            } else if (++scanTick % 8 == 0) {
                if (scanner.verifyBoard(f, boardTop)) {
                    verifyFails = 0;
                } else if (++verifyFails >= 3) {
                    boardTop = -1;
                    verifyFails = 0;
                    resetGame();
                    setStatus("● ПОИСК ДОСКИ...", GRAY);
                    return;
                }
            }
            BoardScanner.Frame view = f;
            if (hlA >= 0 && System.currentTimeMillis() < hlUntil) {
                view = new BoardScanner.Unblend(f, boardTop, hlA, 0xFF000000 | FROM_RGB,
                        hlB, 0xFF000000 | TO_RGB, FILL_ALPHA / 255f);
            }
            process(scanner.observe(view, boardTop));
        } finally {
            img.close();
        }
    }

    private void process(BoardScanner.Obs o) {
        if (BoardScanner.isStartPosition(o)) {
            if (++startStable >= 2 && (!synced || engine.moveCount() > 0)) {
                startNewGame(o);
            }
            return;
        }
        startStable = 0;

        if (!synced) {
            int orient = BoardScanner.trySyncAfterFirstMove(o, engine);
            if (orient != 0) {
                myWhite = orient == 1;
                synced = true;
                appliedMask = o.litMask();
                candMask = -1;
                candCount = 0;
                desyncFrames = 0;
                thinkGen++;
                clearHighlight();
                afterMove();
                return;
            }
            setStatus("● ЖДУ НОВУЮ ПАРТИЮ", GRAY);
            tvPiece.setText("—");
            tvCoords.setText("с начала партии");
            return;
        }

        int mism = 0;
        Position p = engine.position();
        for (int sqr = 0; sqr < 64; sqr++) {
            boolean expected = p.b[sqr] != 0;
            if (expected != o.occ[BoardScanner.squareToScreen(sqr, myWhite)]) mism++;
        }
        if (mism >= 4) {
            if (++desyncFrames >= 8) {
                synced = false;
                thinkGen++;
                engine.cancel();
                clearHighlight();
                setStatus("● РАССИНХРОН", RED);
                tvPiece.setText("—");
                tvCoords.setText("жду новую партию");
                return;
            }
        } else {
            desyncFrames = 0;
        }

        long mask = o.litMask();
        if (mask != candMask) {
            candMask = mask;
            candCount = 1;
            return;
        }
        candCount++;
        if (candCount < 2 || mask == appliedMask) return;
        int bits = Long.bitCount(mask);
        if (bits < 2 || bits > 5) return;

        List<Integer> lit = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if ((mask & (1L << i)) != 0) lit.add(BoardScanner.screenToSquare(i, myWhite));
        }
        int[] litSq = new int[lit.size()];
        for (int i = 0; i < litSq.length; i++) litSq[i] = lit.get(i);

        List<Integer> good = new ArrayList<>();
        for (int m : engine.candidates(litSq)) {
            boolean fromEmpty = !o.occ[BoardScanner.squareToScreen(Position.from(m), myWhite)];
            boolean toOccupied = o.occ[BoardScanner.squareToScreen(Position.to(m), myWhite)];
            if (fromEmpty && toOccupied) good.add(m);
        }
        if (good.size() != 1) return;

        engine.apply(good.get(0));
        appliedMask = mask;
        thinkGen++;
        engine.cancel();
        clearHighlight();
        afterMove();
    }

    private void startNewGame(BoardScanner.Obs o) {
        myWhite = BoardScanner.bottomIsWhite(o);
        engine.newGame();
        synced = true;
        appliedMask = 0;
        candMask = -1;
        candCount = 0;
        desyncFrames = 0;
        thinkGen++;
        engine.cancel();
        clearHighlight();
        afterMove();
    }

    private void resetGame() {
        synced = false;
        startStable = 0;
        appliedMask = 0;
        candMask = -1;
        candCount = 0;
        desyncFrames = 0;
        thinkGen++;
        if (engine != null) engine.cancel();
        clearHighlight();
    }

    private int myColor() { return myWhite ? Position.WHITE : Position.BLACK; }

    private void clearHighlight() {
        if (boardView != null) boardView.clear();
        hlUntil = System.currentTimeMillis() + 700;
    }

    private void afterMove() {
        String side = myWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ";
        if (engine.isGameOver()) {
            setStatus(side + " · ПАРТИЯ ОКОНЧЕНА", GRAY);
            tvPiece.setText("—");
            tvCoords.setText("");
            return;
        }
        if (engine.sideToMove() == myColor()) {
            startThinking();
        } else {
            setStatus(side + " · ХОД СОПЕРНИКА", GRAY);
            tvPiece.setText("—");
            tvCoords.setText("...");
        }
    }

    private void startThinking() {
        final int gen = ++thinkGen;
        setStatus((myWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " · ДУМАЮ...", AMBER);
        tvPiece.setText("•••");
        tvCoords.setText("...");
        final Position snap = engine.snapshot();
        final java.util.HashMap<String, Integer> hist = engine.historySnapshot();
        final ChessEngine eng = engine;
        worker.execute(() -> {
            final ChessEngine.EngineMove m;
            try {
                m = eng.bestMove(snap, hist);
            } catch (Throwable t) {
                return;
            }
            handler.post(() -> {
                if (gen != thinkGen || !synced) return;
                showMove(snap, m);
            });
        });
    }

    private void showMove(Position snap, ChessEngine.EngineMove m) {
        if (m == null) {
            setStatus("ПАРТИЯ ОКОНЧЕНА", GRAY);
            return;
        }
        setStatus((myWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " · ТВОЙ ХОД ✓", GREEN);
        tvPiece.setText(m.pieceName + "  " + m.pieceIcon);
        tvCoords.setText(m.fromCoord + " → " + m.toCoord);
        int a = BoardScanner.squareToScreen(m.from, myWhite);
        int b = BoardScanner.squareToScreen(m.to, myWhite);
        hlA = a;
        hlB = b;
        hlUntil = Long.MAX_VALUE;
        boardView.setMove(a % 8, a / 8, b % 8, b / 8, boardTop, boardW);
    }

    private void onPillTap() {
        if (synced && !engine.isGameOver() && engine.sideToMove() == myColor()) startThinking();
    }

    private void setStatus(String text, int color) {
        tvStatus.setTextColor(color);
        tvStatus.setText(text);
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel("chess_bot_svc", "Assistant", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, "chess_bot_svc")
                .setContentTitle("Chess Assistant")
                .setContentText("Активен")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        thinkGen++;
        if (engine != null) engine.cancel();
        worker.shutdownNow();
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Throwable ignored) { }
        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Throwable ignored) { }
        try { if (imageReader != null) imageReader.close(); } catch (Throwable ignored) { }
        try { if (boardView != null) wm.removeView(boardView); } catch (Throwable ignored) { }
        try { if (pillView != null) wm.removeView(pillView); } catch (Throwable ignored) { }
    }
}