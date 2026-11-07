package com.chessbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
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
    private int currentElo = 1500;

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static class ChessBackgroundView extends View {
        private final Paint darkTilePaint = new Paint();

        public ChessBackgroundView(Context context) {
            super(context);
            darkTilePaint.setColor(Color.parseColor("#10141A"));
            darkTilePaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int tileSize = getWidth() / 8;
            if (tileSize <= 0) return;

            for (int r = 0; r < getHeight() / tileSize + 1; r++) {
                for (int c = 0; c < 8; c++) {
                    if ((r + c) % 2 == 1) {
                        canvas.drawRect(c * tileSize, r * tileSize, (c + 1) * tileSize, (r + 1) * tileSize, darkTilePaint);
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B0E13"));

        ChessBackgroundView bgView = new ChessBackgroundView(this);
        root.addView(bgView);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(40), dp(24), dp(40));

        TextView title = new TextView(this);
        title.setText("CHESS ASSISTANT");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Локальный суфлер позиций");
        subTitle.setTextColor(Color.parseColor("#6F7D8C"));
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitle.setGravity(Gravity.CENTER);
        subTitle.setPadding(0, dp(4), 0, dp(36));
        content.addView(subTitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#151A22"));
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#232B38"));
        card.setBackground(cardBg);

        TextView eloLabel = new TextView(this);
        eloLabel.setText("СИЛА ИГРЫ");
        eloLabel.setTextColor(Color.parseColor("#8E99A8"));
        eloLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        eloLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(eloLabel);

        TextView eloVal = new TextView(this);
        eloVal.setText("1500 ELO");
        eloVal.setTextColor(Color.parseColor("#81B64C"));
        eloVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        eloVal.setTypeface(null, android.graphics.Typeface.BOLD);
        eloVal.setPadding(0, dp(4), 0, dp(12));
        card.addView(eloVal);

        SeekBar slider = new SeekBar(this);
        slider.setMax(2500);
        slider.setProgress(1100);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(22), dp(22));
        thumb.setColor(Color.parseColor("#81B64C"));
        thumb.setStroke(dp(3), Color.WHITE);
        slider.setThumb(thumb);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentElo = 400 + progress;
                eloVal.setText(currentElo + " ELO");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(slider);
        content.addView(card);

        TextView hints = new TextView(this);
        hints.setText("• 1 тап по оверлею — следующий ход\n• 2 тапа — мгновенное закрытие");
        hints.setTextColor(Color.parseColor("#5A6575"));
        hints.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hints.setPadding(0, dp(24), 0, dp(32));
        hints.setLineSpacing(dp(4), 1f);
        content.addView(hints);

        Button btn = new Button(this);
        btn.setText("ЗАПУСТИТЬ ОВЕРЛЕЙ");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#81B64C"));
        btnBg.setCornerRadius(dp(14));
        btn.setBackground(btnBg);
        btn.setPadding(0, dp(14), 0, dp(14));

        btn.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                Intent sIntent = new Intent(this, OverlayService.class);
                sIntent.putExtra("ELO", currentElo);
                startService(sIntent);
                finish();
            }
        });
        content.addView(btn);

        FrameLayout.LayoutParams flParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(content, flParams);

        setContentView(root);
    }
}

