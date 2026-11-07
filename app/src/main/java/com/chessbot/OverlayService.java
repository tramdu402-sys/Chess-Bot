package com.chessbot;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {
    private WindowManager wm;
    private LinearLayout overlay;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        int width = metrics.widthPixels / 3;
        int height = width / 3;

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Color.argb(210, 34, 139, 34)); 
        overlay.setGravity(Gravity.CENTER);

        TextView text = new TextView(this);
        text.setText("Пешка\nE6 - E7\n⏳ 2.34 сек");
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        text.setGravity(Gravity.CENTER);
        overlay.addView(text);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 20; 
        params.y = 20;

        overlay.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    initialX = params.x; 
                    initialY = params.y;
                    touchX = event.getRawX(); 
                    touchY = event.getRawY();
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = initialX - (int)(event.getRawX() - touchX);
                    params.y = initialY + (int)(event.getRawY() - touchY);
                    wm.updateViewLayout(overlay, params);
                    return true;
                }
                return false;
            }
        });
        
        wm.addView(overlay, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlay != null) wm.removeView(overlay);
    }
}
