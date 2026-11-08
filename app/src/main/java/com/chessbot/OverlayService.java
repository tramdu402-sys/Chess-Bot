package com.chessbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.CountDownTimer;
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

public class OverlayService extends Service {
    private WindowManager wm;
    private BoardHighlightView boardView;
    private LinearLayout pillView;
    private TextView tvStatus, tvPiece, tvCoords;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());
    private CountDownTimer timer;

    private int elo = 1500;
    private ChessEngine engine;
    private boolean isMyColorWhite = false;
    private boolean colorDetected = false;
    private boolean firstMoveDone = false;
    private boolean isThinking = false;
    
    private int lastHighlightHash = 0;
    private boolean lastCheckState = false;
    private long lastTapTime = 0;

    private int boardTop = 0;
    private int boardSize = 0;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // Холст полупрозрачной подсветки
    private static class BoardHighlightView extends View {
        private final Paint fromFill = new Paint();
        private final Paint toFill = new Paint();
        private final Paint stroke = new Paint();

        private int fC = -1, fR = -1, tC = -1, tR = -1;
        private int bTop = 0, sq = 0;
        private boolean active = false;

        public BoardHighlightView(Context ctx) {
            super(ctx);
            fromFill.setColor(Color.argb(70, 129, 182, 76)); // Зеленый
            toFill.setColor(Color.argb(80, 56, 189, 248));   // Голубой
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(5f);
            stroke.setColor(Color.argb(220, 129, 182, 76));
        }

        public void setMove(int fC, int fR, int tC, int tR, int top, int size) {
            this.fC = fC; this.fR = fR;
            this.tC = tC; this.tR = tR;
            this.bTop = top;
            this.sq = size / 8;
            this.active = true;
            postInvalidate();
        }

        public void clear() {
            this.active = false;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!active || sq <= 0) return;

            if (fC >= 0 && fR >= 0) {
                float l = fC * sq;
                float t = bTop + (fR * sq);
                canvas.drawRect(l, t, l + sq, t + sq, fromFill);
                canvas.drawRect(l, t, l + sq, t + sq, stroke);
            }
            if (tC >= 0 && tR >= 0) {
                float l = tC * sq;
                float t = bTop + (tR * sq);
                canvas.drawRect(l, t, l + sq, t + sq, toFill);
                canvas.drawRect(l, t, l + sq, t + sq, stroke);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            elo = intent.getIntExtra("ELO", 1500);
            engine = new ChessEngine(elo);

            int code = intent.getIntExtra("RESULT_CODE", -1);
            Intent data = intent.getParcelableExtra("DATA");
            if (code != -1 && data != null && mediaProjection == null) {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = mpm.getMediaProjection(code, data);
                setupScreenCapture();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        boardView = new BoardHighlightView(this);
        WindowManager.LayoutParams boardParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wm.addView(boardView, boardParams);

        pillView = new LinearLayout(this);
        pillView.setOrientation(LinearLayout.VERTICAL);
        pillView.setGravity(Gravity.CENTER);
        pillView.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F606080C"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.5f), Color.parseColor("#81B64C"));
        pillView.setBackground(bg);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#6B7888"));
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
        tvCoords.setTextColor(Color.parseColor("#81B64C"));
        tvCoords.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvCoords.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCoords.setGravity(Gravity.CENTER);
        pillView.addView(tvCoords);

        final WindowManager.LayoutParams pillParams = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.44f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        pillParams.gravity = Gravity.TOP | Gravity.END;
        pillParams.x = dp(16);
        pillParams.y = dp(70);

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
                            pillParams.x = initX - (int)(e.getRawX() - tX);
                            pillParams.y = initY + (int)(e.getRawY() - tY);
                            wm.updateViewLayout(pillView, pillParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            long now = System.currentTimeMillis();
                            if (now - lastTapTime < 350) stopSelf();
                            else autoComputeMove(-1, -1, false);
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(pillView, pillParams);
        tvStatus.setText("● ПОИСК ДОСКИ");
        tvPiece.setText("—");
        tvCoords.setText("...");
    }

    private void setupScreenCapture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ChessLiveAI",
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
        handler.postDelayed(scanLoop, 200);
    }

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            scanScreen();
            handler.postDelayed(this, 250);
        }
    };

    private void scanScreen() {
        if (imageReader == null || isThinking) return;
        Image img = imageReader.acquireLatestImage();
        if (img == null) return;

        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int w = img.getWidth();
            int h = img.getHeight();
            int pStride = plane.getPixelStride();
            int rStride = plane.getRowStride();
            boardSize = w;
            int sq = boardSize / 8;

            // БРОНЕБОЙНЫЙ ПОИСК ДОСКИ: Ищем паттерн (Светлая клетка рядом с Темной)
            if (boardTop == 0) {
                for (int y = (int)(h * 0.15f); y < (int)(h * 0.5f); y += 3) {
                    int off1 = (y * rStride) + ((sq/2) * pStride);
                    int off2 = (y * rStride) + ((sq + sq/2) * pStride);
                    if (off2 < buf.remaining() - 4) {
                        int r1 = buf.get(off1) & 0xFF, g1 = buf.get(off1+1) & 0xFF, b1 = buf.get(off1+2) & 0xFF;
                        int r2 = buf.get(off2) & 0xFF, g2 = buf.get(off2+1) & 0xFF, b2 = buf.get(off2+2) & 0xFF;

                        boolean isL1 = (r1 > 200 && g1 > 200 && b1 > 180);
                        boolean isD1 = (g1 > 120 && r1 > 90 && r1 < 160 && b1 < 120);
                        boolean isL2 = (r2 > 200 && g2 > 200 && b2 > 180);
                        boolean isD2 = (g2 > 120 && r2 > 90 && r2 < 160 && b2 < 120);

                        if ((isL1 && isD2) || (isD1 && isL2)) {
                            boardTop = y;
                            break;
                        }
                    }
                }
            }

            if (boardTop == 0) return; // Ждем пока доска появится на экране

            // ОПРЕДЕЛЕНИЕ ЦВЕТА И АВТОМАТИКА 1 ХОДА
            if (!colorDetected) {
                int sampleY = boardTop + (int)(7.5f * sq);
                int sampleX = (int)(3.5f * sq); // Позиция ферзя/короля
                int offFig = (sampleY * rStride) + (sampleX * pStride);
                if (offFig < buf.remaining() - 4) {
                    int r = buf.get(offFig) & 0xFF, g = buf.get(offFig+1) & 0xFF, b = buf.get(offFig+2) & 0xFF;
                    // Белые фигуры яркие (в среднем > 150)
                    isMyColorWhite = ((r + g + b) / 3) > 150;
                    colorDetected = true;

                    // ЕСЛИ ИГРАЕМ БЕЛЫМИ — ДЕЛАЕМ ПЕРВЫЙ ХОД МОМЕНТАЛЬНО САМИ!
                    if (isMyColorWhite && !firstMoveDone) {
                        firstMoveDone = true;
                        autoComputeMove(-1, -1, false);
                        return;
                    }
                }
            }

            int highlightHash = 0;
            int litCount = 0;
            int lastTargetCol = -1, lastTargetRow = -1;
            boolean kingInCheck = false;

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int px = col * sq + (sq / 2);
                    int py = boardTop + row * sq + (int)(sq * 0.15f);
                    int off = (py * rStride) + (px * pStride);

                    if (off < buf.remaining() - 4) {
                        int red = buf.get(off) & 0xFF, grn = buf.get(off + 1) & 0xFF, blu = buf.get(off + 2) & 0xFF;
                        
                        // Красный шах
                        if (red > 190 && grn < 110 && blu < 110) kingInCheck = true;
                        
                        // Желтый ход соперника
                        if (red > 150 && grn > 150 && blu < 110 && (red - blu > 40)) {
                            highlightHash += (row * 8 + col) * 31;
                            litCount++;
                            lastTargetCol = col;
                            lastTargetRow = row;
                        }
                    }
                }
            }

            // АВТОМАТИКА ОТВЕТА НА ХОД СОПЕРНИКА
            if ((litCount >= 2 && highlightHash != lastHighlightHash) || (kingInCheck && !lastCheckState)) {
                lastHighlightHash = highlightHash;
                lastCheckState = kingInCheck;
                if (!isMyColorWhite) firstMoveDone = true; // Черные тоже начали партию
                autoComputeMove(lastTargetCol, lastTargetRow, kingInCheck);
            }
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    private void autoComputeMove(int targetC, int targetR, boolean inCheck) {
        if (timer != null) timer.cancel();
        boardView.clear();
        isThinking = true;

        tvPiece.setText("•••");
        tvCoords.setText("...");

        ChessEngine.EngineMove move = engine.findMove(isMyColorWhite, targetC, targetR, inCheck);
        long delayMs = (long) (move.delaySec * 1000);

        if (delayMs <= 200) {
            isThinking = false;
            tvStatus.setTextColor(Color.parseColor("#81B64C"));
            tvStatus.setText((isMyColorWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " ✓");
            tvPiece.setText(move.pieceName + "  " + move.pieceIcon);
            tvCoords.setText(move.fromCoord + " → " + move.toCoord);
            boardView.setMove(move.fromCol, move.fromRow, move.toCol, move.toRow, boardTop, boardSize);
            return;
        }

        timer = new CountDownTimer(delayMs, 40) {
            @Override
            public void onTick(long millisLeft) {
                tvStatus.setTextColor(Color.parseColor("#E5A93C"));
                tvStatus.setText(String.format("⏳ %.1fс", millisLeft / 1000f));
            }
            @Override
            public void onFinish() {
                isThinking = false;
                tvStatus.setTextColor(Color.parseColor("#81B64C"));
                tvStatus.setText((isMyColorWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " ✓");
                tvPiece.setText(move.pieceName + "  " + move.pieceIcon);
                tvCoords.setText(move.fromCoord + " → " + move.toCoord);
                boardView.setMove(move.fromCol, move.fromRow, move.toCol, move.toRow, boardTop, boardSize);
            }
        }.start();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel("chess_bot_svc", "Assistant", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, "chess_bot_svc").setContentTitle("Chess Assistant").setContentText("Активен").setSmallIcon(android.R.drawable.ic_menu_compass).build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanLoop);
        if (timer != null) timer.cancel();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();
        if (boardView != null) wm.removeView(boardView);
        if (pillView != null) wm.removeView(pillView);
    }
}

