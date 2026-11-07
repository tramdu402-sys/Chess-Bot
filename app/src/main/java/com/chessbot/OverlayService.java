package com.chessbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
    private TextView tvStatus, tvMove;
    private CountDownTimer timer;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int elo = 1500;
    private int moveCount = 1;
    private boolean isBlack = true;
    private boolean waitingForMyTurn = true;
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
        startForeground(1, buildForegroundNotification());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(10), dp(4), dp(10), dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EE12161E"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1.5f), Color.parseColor("#81B64C"));
        overlay.setBackground(bg);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(Color.parseColor("#8E99A8"));
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        overlay.addView(tvStatus);

        tvMove = new TextView(this);
        tvMove.setTextColor(Color.WHITE);
        tvMove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvMove.setTypeface(null, android.graphics.Typeface.BOLD);
        overlay.addView(tvMove);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.40f), dp(48),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(70);

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
                                stopSelf(); // Двойной тап — выход
                            } else {
                                startEvaluationCycle(); // Одиночный тап — принудительный пересчёт
                            }
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(overlay, params);
        tvStatus.setText("● ОЖИДАНИЕ ХОДА");
        tvMove.setText("•••");
    }

    private void setupScreenCapture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay("ChessScanner",
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        // Периодическое сканирование экрана на появление подсветки хода
        handler.postDelayed(scanRunnable, 600);
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            analyzeScreenFrame();
            handler.postDelayed(this, 700);
        }
    };

    private void analyzeScreenFrame() {
        if (imageReader == null) return;
        Image img = imageReader.acquireLatestImage();
        if (img == null) return;

        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int width = img.getWidth();
            int height = img.getHeight();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();

            // Автоопределение цвета: смотрим нижнюю половину доски (район 65% высоты экрана)
            // и анализируем наличие желто-зеленой подсветки Chess.com (#F5F682 / #BACA44)
            int boardCenterY = (int) (height * 0.55f);
            int checkX = width / 2;
            int pixelOffset = (boardCenterY * rowStride) + (checkX * pixelStride);

            if (pixelOffset < buffer.remaining() - 4) {
                int r = buffer.get(pixelOffset) & 0xFF;
                int g = buffer.get(pixelOffset + 1) & 0xFF;
                int b = buffer.get(pixelOffset + 2) & 0xFF;

                // Детекция подсветки хода соперника: жёлтый цвет (высокий R и G, низкий B)
                boolean moveHighlight = (r > 190 && g > 190 && b < 140);
                if (moveHighlight && waitingForMyTurn) {
                    waitingForMyTurn = false;
                    startEvaluationCycle();
                }
            }
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    private void startEvaluationCycle() {
        if (timer != null) timer.cancel();

        tvMove.setTextColor(Color.parseColor("#5A6575"));
        tvMove.setText("•••");

        // Честный расчёт времени раздумий под ELO
        ChessEngine.MoveDecision decision = ChessEngine.getBestMove(isBlack, moveCount, elo);
        long durationMs = (long) (decision.delaySeconds * 1000);

        timer = new CountDownTimer(durationMs, 30) {
            @Override
            public void onTick(long millisLeft) {
                float sec = millisLeft / 1000f;
                tvStatus.setTextColor(Color.parseColor("#E5A93C"));
                tvStatus.setText(String.format("⏳  %.2f сек", sec));
            }

            @Override
            public void onFinish() {
                tvStatus.setTextColor(Color.parseColor("#81B64C"));
                tvStatus.setText(isBlack ? "ЧЁРНЫЕ ✓" : "БЕЛЫЕ ✓");

                // Ход открывается только после завершения реального счёта
                tvMove.setTextColor(Color.WHITE);
                tvMove.setText(decision.moveText);
                moveCount++;
                waitingForMyTurn = true;
            }
        }.start();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                "chess_bot_svc", "Chess Assistant Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildForegroundNotification() {
        return new Notification.Builder(this, "chess_bot_svc")
                .setContentTitle("Chess Assistant")
                .setContentText("Авто-суфлер активен")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(scanRunnable);
        if (timer != null) timer.cancel();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (imageReader != null) imageReader.close();
        if (overlay != null) wm.removeView(overlay);
    }
}
