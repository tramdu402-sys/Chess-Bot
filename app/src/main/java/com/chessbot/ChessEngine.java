package com.chessbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChessEngine {
    private static final Random RNG = new Random();

    public static class MoveDecision {
        public final String moveText;
        public final float delaySeconds;

        public MoveDecision(String moveText, float delaySeconds) {
            this.moveText = moveText;
            this.delaySeconds = delaySeconds;
        }
    }

    // Расчет времени по позиции и ELO (антидетект)
    public static float calculateThinkingTime(int moveNumber, boolean isCapture, int elo) {
        float baseTime;
        if (moveNumber <= 6) {
            // Дебютные ходы делаются быстрее
            baseTime = 1.3f + (RNG.nextFloat() * 1.1f);
        } else if (isCapture) {
            // Взятия и размены занимают меньше времени
            baseTime = 1.1f + (RNG.nextFloat() * 0.9f);
        } else {
            // Миттельшпиль: глубокая позиционная оценка
            float eloFactor = Math.max(0.7f, 1.8f - (elo / 2200f));
            baseTime = (2.4f * eloFactor) + (RNG.nextFloat() * 2.2f);
        }
        // Защита от одинаковых таймингов (джиттер)
        return Math.round((baseTime + (RNG.nextFloat() * 0.4f)) * 100f) / 100f;
    }

    // Генерация ходов в зависимости от обнаруженного цвета
    public static MoveDecision getBestMove(boolean isBlack, int moveCount, int elo) {
        String[] blackMoves = {"♟ e7 → e5", "♞ g8 → f6", "♟ c7 → c5", "♟ d7 → d5", "♝ f8 → c5", "♞ b8 → c6"};
        String[] whiteMoves = {"♟ e2 → e4", "♟ d2 → d4", "♞ g1 → f3", "♟ c2 → c4", "♝ f1 → c4", "♞ b1 → c3"};

        String move = isBlack 
                ? blackMoves[RNG.nextInt(blackMoves.length)]
                : whiteMoves[RNG.nextInt(whiteMoves.length)];

        boolean isCapture = RNG.nextFloat() > 0.75f;
        float delay = calculateThinkingTime(moveCount, isCapture, elo);
        return new MoveDecision(move, delay);
    }
}
