package com.chessbot;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("CHESS BOT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 100);
        layout.addView(title);

        TextView eloText = new TextView(this);
        eloText.setText("Уровень ELO: 1400");
        eloText.setTextColor(Color.WHITE);
        eloText.setTextSize(16);
        eloText.setGravity(Gravity.CENTER);
        layout.addView(eloText);

        SeekBar eloSlider = new SeekBar(this);
        eloSlider.setMax(2500); // 2900 - 400
        eloSlider.setProgress(1000); // 1400 - 400
        eloSlider.setPadding(0, 50, 0, 100);
        eloSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int currentElo = 400 + progress;
                eloText.setText("Уровень ELO: " + currentElo);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(eloSlider);

        Button btnPlay = new Button(this);
        btnPlay.setText("ИГРАТЬ");
        btnPlay.setBackgroundColor(Color.parseColor("#228B22"));
        btnPlay.setTextColor(Color.WHITE);
        btnPlay.setTextSize(20);
        btnPlay.setPadding(0, 30, 0, 30);
        
        btnPlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                startService(new Intent(this, OverlayService.class));
                finish();
            }
        });
        layout.addView(btnPlay);
        setContentView(layout);
    }
}
