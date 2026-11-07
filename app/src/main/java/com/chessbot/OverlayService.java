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
    private TextView tvLine1, tvLine2, tvLine3;
    private CountDownTimer timer;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int elo = 1500;
    private int moveCount = 1;
    private boolean isMyColorWhite = true;
    private boolean waitingForMyTurn = true;
    private long lastTapTime = 0;
    private int lastHighlightHash = 0;

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
        overlay.setPadding(dp(12), dp(8), dp(12), dp(8));

        // AMOLED-капсула со скруглением и окантовкой
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F40D1117"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.2f), Color.parseColor("#81B64C"));
        overlay.setBackground(bg);

        // Строка 1: Статус / Таймер
        tvLine1 = new TextView(this);
        tvLine1.setTextColor(Color.parseColor("#7D8B9B"));
        tvLine1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tvLine1.setGravity(Gravity.CENTER);
        overlay.addView(tvLine1);

        // Строка 2: Фигура и крупный значок
        tvLine2 = new TextView(this);
        tvLine2.setTextColor(Color.WHITE);
        tvLine2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLine2.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLine2.setGravity(Gravity.CENTER);
        tvLine2.setPadding(0, dp(1), 0, dp(1));
        overlay.addView(tvLine2);

        // Строка 3: Координаты хода
        tvLine3 = new TextView(this);
        tvLine3.setTextColor(Color.parseColor("#81B64C"));
        tvLine3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvLine3.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLine3.setGravity(Gravity.CENTER);
        overlay.addView(tvLine3);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (dm.widthPixels * 0.42f), WindowManager.LayoutParams.WRAP_CONTENT,
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
                                stopSelf(); // Двойной тап — закрытие
                            } else {
                                startEvaluationCycle(); // Одиночный тап — пересчет
                            }
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(overlay, params);
        tvLine1.setText("● ОЖИДАНИЕ ХОДА");
        tvLine2.setText("—");
        tvLine3.setText("...");
    }

    private void setupScreenCapture() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay("ChessScanner",
                dm.widthPixels, dm.heightPixels, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        handler.postDelayed(scanRunnable, 500);
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            scanBoardState();
            handler.postDelayed(this, 600);
        }
    };

    private void scanBoardState() {
        if (imageReader == null) return;
        Image img = imageReader.acquireLatestImage();
        if (img == null) return;

        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int width = img.getWidth();
            int height = img.getHeight();
            int pStride = plane.getPixelStride();
            int rStride = plane.getRowStride();

            // Сканируем строго низ экрана (72% высоты), где стоят наши основные фигуры
            int bottomAreaY = (int) (height * 0.72f);
            int sampleX = width / 2;
            int offset = (bottomAreaY * rStride) + (sampleX * pStride);

            if (offset < buf.remaining() - 4) {
                int r = buf.get(offset) & 0xFF;
                int g = buf.get(offset + 1) & 0xFF;
                int b = buf.get(offset + 2) & 0xFF;

                // Если снизу фигуры белые (светлые пиксели) — мы играем белыми, иначе чёрными
                int brightness = (r + g + b) / 3;
                isMyColorWhite = (brightness > 120);

                // Поиск желтой подсветки хода Chess.com (#F5F682)
                boolean isYellowHighlight = (r > 185 && g > 185 && b < 140);
                int currentHash = (isYellowHighlight ? 1 : 0) + (r * 10) + g;

                if (isYellowHighlight && (currentHash != lastHighlightHash) && waitingForMyTurn) {
                    lastHighlightHash = currentHash;
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

        tvLine2.setText("•••");
        tvLine3.setText("...");

        // Получаем ход с расчетом фигуры и тайминга
        ChessEngine.MoveDecision decision = ChessEngine.getBestMove(!isMyColorWhite, moveCount, elo);
        long durationMs = (long) (decision.delaySeconds * 1000);

        timer = new CountDownTimer(durationMs, 30) {
            @Override
            public void onTick(long millisLeft) {
                float sec = millisLeft / 1000f;
                tvLine1.setTextColor(Color.parseColor("#E5A93C"));
                tvLine1.setText(String.format("⏳ %.2f сек", sec));
            }

            @Override
            public void onFinish() {
                tvLine1.setTextColor(Color.parseColor("#81B64C"));
                tvLine1.setText((isMyColorWhite ? "БЕЛЫЕ" : "ЧЁРНЫЕ") + " ✓");

                // Строка 2: Название и значок
                tvLine2.setText(decision.pieceName + " " + decision.pieceIcon);
                // Строка 3: Координаты
                tvLine3.setText(decision.fromCoord + " → " + decision.toCoord);

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

