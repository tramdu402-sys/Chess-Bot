package com.chessbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
    private LinearLayout overlay;
    private TextView tvStatus, tvPiece, tvCoords;
    private CountDownTimer timer;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int elo = 1500;
    private ChessEngine engine;
    private boolean isMyColorWhite = false;
    private boolean colorDetected = false;
    private boolean isThinking = false;
    private int lastHighlightHash = 0;
    private long lastTapTime = 0;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
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

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(14), dp(8), dp(14), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F705070A"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#81B64C"));
        overlay.setBackground(bg);

        // Строка 1: Статус / Таймер
        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#6C7A8C"));
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvStatus.setGravity(Gravity.CENTER);
        overlay.addView(tvStatus);

        // Строка 2: Фигура со значком
        tvPiece = new TextView(this);
        tvPiece.setTextColor(Color.WHITE);
        tvPiece.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvPiece.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPiece.setGravity(Gravity.CENTER);
        overlay.addView(tvPiece);

        // Строка 3: Координаты хода
        tvCoords = new TextView(this);
        tvCoords.setTextColor(Color.parseColor("#81B64C"));
        tvCoords.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvCoords.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCoords.setGravity(Gravity.CENTER);
        overlay.addView(tvCoords);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.40f), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(75);

        overlay.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float tX, tY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x; initY = params.y;
                        tX = e.getRawX(); tY = e.getRawY();
                        isDrag = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - tX) > 12 || Math.abs(e.getRawY() - tY) > 12) {
                            isDrag = true;
                            params.x = initX - (int)(e.getRawX() - tX);
                            params.y = initY + (int)(e.getRawY() - tY);
                            wm.updateViewLayout(overlay, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            long now = System.currentTimeMillis();
                            if (now - lastTapTime < 350) {
                                stopSelf(); // Двойной тап — мгновенное закрытие
                            } else {
                                calculateAndShowMove(); // Одиночный тап — принудительный ход
                            }
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(overlay, params);
        tvStatus.setText("● СКАНИРОВАНИЕ");
        tvPiece.setText("—");
        tvCoords.setText("...");
    }

    private void setupScreenCapture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay("BoardReader",
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        handler.postDelayed(scanLoop, 350);
    }

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            scanScreen();
            handler.postDelayed(this, 400);
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

            // Динамический поиск доски: квадрат посередине экрана
            int boardSize = w;
            int boardTop = (h - boardSize) / 2;
            int sqSize = boardSize / 8;

            // 1. Определение цвета игрока по фигурам на нижнем ряду (7-я горизонталь экрана)
            if (!colorDetected) {
                int totalBrightness = 0;
                int samples = 0;
                for (int c = 1; c <= 6; c++) {
                    int px = c * sqSize + (sqSize / 2);
                    int py = boardTop + (int)(7.5f * sqSize);
                    int off = (py * rStride) + (px * pStride);
                    if (off < buf.remaining() - 4) {
                        int r = buf.get(off) & 0xFF;
                        int g = buf.get(off + 1) & 0xFF;
                        int b = buf.get(off + 2) & 0xFF;
                        totalBrightness += (r + g + b) / 3;
                        samples++;
                    }
                }
                if (samples > 0) {
                    // У чёрных фигур средняя яркость меньше 105
                    isMyColorWhite = (totalBrightness / samples) > 115;
                    colorDetected = true;
                    tvStatus.setText((isMyColorWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " ● ЖДЁМ");
                }
            }

            // 2. Детектор желтой подсветки хода Chess.com (клетки с высоким R и G, но низким B)
            int highlightHash = 0;
            int litSquares = 0;

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    int px = c * sqSize + (int)(sqSize * 0.15f);
                    int py = boardTop + r * sqSize + (int)(sqSize * 0.15f);
                    int off = (py * rStride) + (px * pStride);

                    if (off < buf.remaining() - 4) {
                        int red = buf.get(off) & 0xFF;
                        int grn = buf.get(off + 1) & 0xFF;
                        int blu = buf.get(off + 2) & 0xFF;

                        // Желто-зеленая подсветка сделанного хода
                        if (red > 170 && grn > 175 && blu < 155 && (red - blu > 30)) {
                            highlightHash += (r * 8 + c) * 31;
                            litSquares++;
                        }
                    }
                }
            }

            // Если замечена новая подсветка хода соперника
            if (litSquares >= 2 && highlightHash != lastHighlightHash) {
                lastHighlightHash = highlightHash;
                calculateAndShowMove();
            }
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    private void calculateAndShowMove() {
        if (timer != null) timer.cancel();
        isThinking = true;

        tvPiece.setText("•••");
        tvCoords.setText("...");

        ChessEngine.MoveDecision decision = engine.getNextMove(isMyColorWhite);
        long delayMs = (long) (decision.thinkingSeconds * 1000);

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
                tvStatus.setText(isMyColorWhite ? "БЕЛЫЕ ✓" : "ЧЁРНЫЕ ✓");

                // Строка 2: Фигура со значком
                tvPiece.setText(decision.pieceName + " " + decision.pieceIcon);
                // Строка 3: Координаты
                tvCoords.setText(decision.fromSquare + " → " + decision.toSquare);
            }
        }.start();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                "chess_bot_svc", "Assistant", NotificationManager.IMPORTANCE_LOW);
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
        if (overlay != null) wm.removeView(overlay);
    }
}
