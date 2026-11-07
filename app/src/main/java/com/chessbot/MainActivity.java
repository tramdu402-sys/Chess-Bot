package com.chessbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
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

    private static class ChessBackgroundView extends View {
        private final Paint darkPaint = new Paint();

        public ChessBackgroundView(Context ctx) {
            super(ctx);
            darkPaint.setColor(Color.parseColor("#0C1017"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int size = getWidth() / 8;
            if (size <= 0) return;
            for (int r = 0; r < getHeight() / size + 1; r++) {
                for (int c = 0; c < 8; c++) {
                    if ((r + c) % 2 == 1) {
                        canvas.drawRect(c * size, r * size, (c + 1) * size, (r + 1) * size, darkPaint);
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#06080B"));
        root.addView(new ChessBackgroundView(this));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(40), dp(24), dp(40));

        TextView title = new TextView(this);
        title.setText("CHESS SUFFLEUR");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        content.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Автоматическое чтение экрана");
        subTitle.setTextColor(Color.parseColor("#5A6578"));
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitle.setPadding(0, dp(6), 0, dp(36));
        content.addView(subTitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#11151D"));
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#1E2430"));
        card.setBackground(cardBg);

        TextView eloLabel = new TextView(this);
        eloLabel.setText("СИЛА ИГРЫ (ШАГ 50 ELO)");
        eloLabel.setTextColor(Color.parseColor("#7D8B9B"));
        eloLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        eloLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(eloLabel);

        TextView eloVal = new TextView(this);
        eloVal.setText("1500 ELO");
        eloVal.setTextColor(Color.parseColor("#81B64C"));
        eloVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        eloVal.setTypeface(null, android.graphics.Typeface.BOLD);
        eloVal.setPadding(0, dp(4), 0, dp(14));
        card.addView(eloVal);

        SeekBar slider = new SeekBar(this);
        // Диапазон от 400 до 2800 с шагом 50: всего 48 делений ((2800 - 400) / 50)
        slider.setMax(48);
        slider.setProgress(22); // 400 + 22 * 50 = 1500 ELO

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(20), dp(20));
        thumb.setColor(Color.parseColor("#81B64C"));
        thumb.setStroke(dp(2), Color.WHITE);
        slider.setThumb(thumb);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentElo = 400 + (progress * 50);
                eloVal.setText(currentElo + " ELO");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(slider);
        content.addView(card);

        TextView hints = new TextView(this);
        hints.setText("• Сам распознает цвет и ход соперника\n• Одиночный тап — принудительный пересчет\n• Двойной тап — мгновенное закрытие");
        hints.setTextColor(Color.parseColor("#505A69"));
        hints.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hints.setPadding(0, dp(24), 0, dp(32));
        hints.setLineSpacing(dp(4), 1f);
        content.addView(hints);

        Button btn = new Button(this);
        btn.setText("ЗАПУСТИТЬ ОВЕРЛЕЙ");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#81B64C"));
        btnBg.setCornerRadius(dp(12));
        btn.setBackground(btnBg);
        btn.setPadding(0, dp(14), 0, dp(14));

        btn.setOnClickListener(v -> checkAndLaunch());
        content.addView(btn);

        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(content, fl);
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
