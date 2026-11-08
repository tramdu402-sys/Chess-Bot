package com.chessbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;
    private int currentElo = 1500;
    private MediaProjectionManager projectionManager;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));

        TextView eloVal = new TextView(this);
        eloVal.setText("1500");
        eloVal.setTextColor(Color.WHITE);
        eloVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        eloVal.setTypeface(null, android.graphics.Typeface.BOLD);
        eloVal.setGravity(Gravity.CENTER);
        root.addView(eloVal);

        TextView eloSub = new TextView(this);
        eloSub.setText("ELO RATING");
        eloSub.setTextColor(Color.parseColor("#81B64C"));
        eloSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        eloSub.setTypeface(null, android.graphics.Typeface.BOLD);
        eloSub.setLetterSpacing(0.18f);
        eloSub.setGravity(Gravity.CENTER);
        eloSub.setPadding(0, 0, 0, dp(32));
        root.addView(eloSub);

        SeekBar slider = new SeekBar(this);
        slider.setMax(48);
        slider.setProgress(22);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(18), dp(18));
        thumb.setColor(Color.parseColor("#81B64C"));
        slider.setThumb(thumb);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentElo = 400 + (progress * 50);
                eloVal.setText(String.valueOf(currentElo));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(slider);

        Button btn = new Button(this);
        btn.setText("START");
        btn.setTextColor(Color.BLACK);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setLetterSpacing(0.12f);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#81B64C"));
        btnBg.setCornerRadius(dp(24));
        btn.setBackground(btnBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnParams.setMargins(0, dp(40), 0, 0);
        btn.setLayoutParams(btnParams);

        btn.setOnClickListener(v -> checkAndLaunch());
        root.addView(btn);

        setContentView(root);
    }

    private void checkAndLaunch() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())),
                    REQ_OVERLAY);
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) checkAndLaunch();
        } else if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent sIntent = new Intent(this, OverlayService.class);
                sIntent.putExtra("RESULT_CODE", resultCode);
                sIntent.putExtra("DATA", data);
                sIntent.putExtra("ELO", currentElo);
                startForegroundService(sIntent);
                finish();
            }
        }
    }
}