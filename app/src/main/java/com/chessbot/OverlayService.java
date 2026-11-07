package com.chessbot;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class OverlayService extends Service {
    private WindowManager wm;
    private LinearLayout overlay;
    private TextView tvStatus, tvMove;
    private CountDownTimer timer;
    private int elo = 1500;
    private final Random random = new Random();
    private long lastTapTime = 0;

    private final String[] PIECES = {"♞ Конь", "♝ Слон", "♜ Ладья", "♛ Ферзь", "♟ Пешка", "♚ Король"};
    private final String[] COLS = {"a", "b", "c", "d", "e", "f", "g", "h"};

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            elo = intent.getIntExtra("ELO", 1500);
        }
        startMoveCycle();
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        int width = (int) (dm.widthPixels * 0.38f);
        int height = dp(46);

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
        tvStatus.setGravity(Gravity.CENTER);
        overlay.addView(tvStatus);

        tvMove = new TextView(this);
        tvMove.setTextColor(Color.WHITE);
        tvMove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvMove.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMove.setGravity(Gravity.CENTER);
        overlay.addView(tvMove);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(70);

        overlay.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        isDrag = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - touchX) > 12 || Math.abs(event.getRawY() - touchY) > 12) {
                            isDrag = true;
                            params.x = initialX - (int)(event.getRawX() - touchX);
                            params.y = initialY + (int)(event.getRawY() - touchY);
                            wm.updateViewLayout(overlay, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            long now = System.currentTimeMillis();
                            if (now - lastTapTime < 350) {
                                stopSelf();
                            } else {
                                startMoveCycle();
                            }
                            lastTapTime = now;
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(overlay, params);
    }

    private void startMoveCycle() {
        if (timer != null) timer.cancel();

        tvMove.setText("•••");
        tvMove.setTextColor(Color.parseColor("#5A6575"));

        float baseTime = Math.max(1.6f, 4.8f - (elo / 900.0f));
        long duration = (long)((baseTime + (random.nextFloat() * 1.5f)) * 1000);

        timer = new CountDownTimer(duration, 40) {
            @Override
            public void onTick(long millisLeft) {
                float sec = millisLeft / 1000f;
                tvStatus.setTextColor(Color.parseColor("#E5A93C"));
                tvStatus.setText(String.format("⏳  %.2f сек", sec));
            }

            @Override
            public void onFinish() {
                tvStatus.setTextColor(Color.parseColor("#81B64C"));
                tvStatus.setText("✓ ГОТОВО");

                String piece = PIECES[random.nextInt(PIECES.length)];
                String from = COLS[random.nextInt(COLS.length)] + (random.nextInt(8) + 1);
                String to = COLS[random.nextInt(COLS.length)] + (random.nextInt(8) + 1);

                tvMove.setTextColor(Color.WHITE);
                tvMove.setText(piece + "  " + from + " → " + to);
            }
        }.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (overlay != null) wm.removeView(overlay);
    }
}
