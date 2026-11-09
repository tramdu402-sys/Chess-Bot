package com.chessbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;
    private static final int GREEN = Color.parseColor("#81B64C");
    private static final String PREFS = "chessbot";

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

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("elo", 1500);
        if (saved < 400 || saved > 2800) saved = 1500;
        currentElo = (saved / 50) * 50;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(40), 0, dp(40), dp(56));

        View topSpace = new View(this);
        root.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1f));

        final TextView eloVal = new TextView(this);
        eloVal.setText(String.valueOf(currentElo));
        eloVal.setTextColor(Color.WHITE);
        eloVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        eloVal.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        eloVal.setGravity(Gravity.CENTER);
        eloVal.setIncludeFontPadding(false);
        root.addView(eloVal);

        SeekBar slider = new SeekBar(this);
        slider.setMax(48);
        slider.setProgress((currentElo - 400) / 50);
        slider.setPadding(dp(8), 0, dp(8), 0);

        GradientDrawable track = new GradientDrawable();
        track.setColor(Color.parseColor("#26FFFFFF"));
        track.setCornerRadius(dp(2));
        track.setSize(0, dp(2));
        GradientDrawable prog = new GradientDrawable();
        prog.setColor(GREEN);
        prog.setCornerRadius(dp(2));
        prog.setSize(0, dp(2));
        ClipDrawable clip = new ClipDrawable(prog, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        slider.setProgressDrawable(layers);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(14), dp(14));
        thumb.setColor(GREEN);
        slider.setThumb(thumb);
        slider.setSplitTrack(false);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentElo = 400 + (progress * 50);
                eloVal.setText(String.valueOf(currentElo));
                if (fromUser) seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("elo", currentElo).apply();
            }
        });
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        sliderParams.setMargins(0, dp(20), 0, 0);
        root.addView(slider, sliderParams);

        View bottomSpace = new View(this);
        root.addView(bottomSpace, new LinearLayout.LayoutParams(1, 0, 1f));

        Button btn = new Button(this);
        btn.setText("START");
        btn.setAllCaps(false);
        btn.setTextColor(GREEN);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setLetterSpacing(0.3f);
        btn.setStateListAnimator(null);
        btn.setElevation(0);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.TRANSPARENT);
        btnBg.setCornerRadius(dp(28));
        btnBg.setStroke(dp(1), GREEN);
        btn.setBackground(btnBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        root.addView(btn, btnParams);
        btn.setOnClickListener(v -> checkAndLaunch());

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
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("elo", currentElo).apply();
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