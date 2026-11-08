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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;

public class OverlayService extends Service {
    private WindowManager wm;
    private FrameLayout rootOverlay;
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
    private boolean isThinking = false;
    private int lastHighlightHash = 0;
    private long lastTapTime = 0;

    private int boardTop = 0;
    private int boardSize = 0;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // Подсветка полей прямо на шахматной доске (20% прозрачности)
    private static class BoardHighlightView extends View {
        private final Paint fromFill = new Paint();
        private final Paint toFill = new Paint();
        private final Paint borderPaint = new Paint();

        private int fC = -1, fR = -1, tC = -1, tR = -1;
        private int bTop = 0, sq = 0;
        private boolean active = false;

        public BoardHighlightView(Context ctx) {
            super(ctx);
            fromFill.setColor(Color.argb(55, 129, 182, 76)); // Зеленый 20%
            toFill.setColor(Color.argb(65, 56, 189, 248));   // Лазурный 25%

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4.5f);
            borderPaint.setColor(Color.argb(180, 129, 182, 76));
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
                canvas.drawRect(l + 2, t + 2, l + sq - 2, t + sq - 2, borderPaint);
            }

            if (tC >= 0 && tR >= 0) {
                float l = tC * sq;
                float t = bTop + (tR * sq);
                canvas.drawRect(l, t, l + sq, t + sq, toFill);
                canvas.drawRect(l + 2, t + 2, l + sq - 2, t + sq - 2, borderPaint);
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

        rootOverlay = new FrameLayout(this);

        boardView = new BoardHighlightView(this);
        rootOverlay.addView(boardView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        pillView = new LinearLayout(this);
        pillView.setOrientation(LinearLayout.VERTICAL);
        pillView.setGravity(Gravity.CENTER);
        pillView.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F606080C"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.2f), Color.parseColor("#81B64C"));
        pillView.setBackground(bg);

        // Строка 1: Статус
        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#6B7888"));
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvStatus.setGravity(Gravity.CENTER);
        pillView.addView(tvStatus);

        // Строка 2: Фигура со значком и белой обводкой для контраста
        tvPiece = new TextView(this);
        tvPiece.setTextColor(Color.WHITE);
        tvPiece.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPiece.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPiece.setGravity(Gravity.CENTER);
        tvPiece.setShadowLayer(6.0f, 0, 0, Color.WHITE);
        tvPiece.setPadding(0, dp(2), 0, dp(2));
        pillView.addView(tvPiece);

        // Строка 3: Координаты
        tvCoords = new TextView(this);
        tvCoords.setTextColor(Color.parseColor("#81B64C"));
        tvCoords.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvCoords.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCoords.setGravity(Gravity.CENTER);
        pillView.addView(tvCoords);

        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                (int) (dm.widthPixels * 0.44f), FrameLayout.LayoutParams.WRAP_CONTENT);
        pillParams.gravity = Gravity.TOP | Gravity.END;
        pillParams.setMargins(0, dp(60), dp(16), 0);
        rootOverlay.addView(pillView, pillParams);

        WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        pillView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 350) {
                    stopSelf();
                } else {
                    autoComputeMove(3, 4);
                }
                lastTapTime = now;
                return true;
            }
            return true;
        });

        wm.addView(rootOverlay, wmParams);
        tvStatus.setText("● ОЖИДАНИЕ");
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

        handler.postDelayed(scanLoop, 250);
    }

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            scanScreen();
            handler.postDelayed(this, 300);
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

            if (boardTop == 0) {
                int cx = w / 4;
                for (int y = (int)(h * 0.25f); y < (int)(h * 0.45f); y += 4) {
                    int off = (y * rStride) + (cx * pStride);
                    if (off < buf.remaining() - 4) {
                        int r = buf.get(off) & 0xFF;
                        int g = buf.get(off + 1) & 0xFF;
                        int b = buf.get(off + 2) & 0xFF;
                        if (g > 130 && r > 100 && r < 140 && b < 100) {
                            boardTop = y - 8;
                            boardSize = w;
                            break;
                        }
                    }
                }
            }

            if (boardTop == 0) {
                boardTop = (int) (h * 0.312f);
                boardSize = w;
            }

            int sq = boardSize / 8;

            int sampleY = boardTop + (int)(7.5f * sq);
            int sampleX = (int)(2.5f * sq);
            int offFig = (sampleY * rStride) + (sampleX * pStride);
            if (offFig < buf.remaining() - 4) {
                int r = buf.get(offFig) & 0xFF;
                int g = buf.get(offFig + 1) & 0xFF;
                int b = buf.get(offFig + 2) & 0xFF;
                isMyColorWhite = ((r + g + b) / 3) > 115;
            }

            int highlightHash = 0;
            int litCount = 0;
            int lastTargetCol = -1, lastTargetRow = -1;

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    int px = c * sq + (sq / 2);
                    int py = boardTop + r * sq + (int)(sq * 0.15f);
                    int off = (py * rStride) + (px * pStride);

                    if (off < buf.remaining() - 4) {
                        int red = buf.get(off) & 0xFF;
                        int grn = buf.get(off + 1) & 0xFF;
                        int blu = buf.get(off + 2) & 0xFF;

                        if (red > 170 && grn > 175 && blu < 155 && (red - blu > 30)) {
                            highlightHash += (r * 8 + c) * 31;
                            litCount++;
                            lastTargetCol = c;
                            lastTargetRow = r;
                        }
                    }
                }
            }

            if (litCount >= 2 && highlightHash != lastHighlightHash) {
                lastHighlightHash = highlightHash;
                autoComputeMove(lastTargetCol, lastTargetRow);
            }
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    private void autoComputeMove(int targetC, int targetR) {
        if (timer != null) timer.cancel();
        boardView.clear();

        ChessEngine.EngineMove move = engine.findMove(isMyColorWhite, targetC, targetR);
        long delayMs = (long) (move.delaySec * 1000);

        // Если задержка минимальная (< 0.35s), ход выдается сразу же
        if (delayMs <= 350) {
            tvStatus.setTextColor(Color.parseColor("#81B64C"));
            tvStatus.setText((isMyColorWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " ✓");
            tvPiece.setText(move.pieceName + "  " + move.pieceIcon);
            tvCoords.setText(move.fromCoord + " → " + move.toCoord);
            boardView.setMove(move.fromCol, move.fromRow, move.toCol, move.toRow, boardTop, boardSize);
            return;
        }

        isThinking = true;
        tvPiece.setText("•••");
        tvCoords.setText("...");

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
        NotificationChannel ch = new NotificationChannel(
                "chess_bot_svc", "Chess Assistant", NotificationManager.IMPORTANCE_LOW);
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
        handler.removeCallbacks(scanLoop);
        if (timer != null) timer.cancel();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();
        if (rootOverlay != null) wm.removeView(rootOverlay);
    }
}

