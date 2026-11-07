package com.chessbot;

import java.util.Random;

public class ChessEngine {
    private static final Random RNG = new Random();

    public static class MoveDecision {
        public final String pieceName;
        public final String pieceIcon;
        public final String fromCoord;
        public final String toCoord;
        public final float delaySeconds;

        public MoveDecision(String pieceName, String pieceIcon, String fromCoord, String toCoord, float delaySeconds) {
            this.pieceName = pieceName;
            this.pieceIcon = pieceIcon;
            this.fromCoord = fromCoord;
            this.toCoord = toCoord;
            this.delaySeconds = delaySeconds;
        }
    }

    public static float calculateThinkingTime(int moveNumber, boolean isCapture, int elo) {
        float baseTime;
        if (moveNumber <= 6) {
            baseTime = 1.3f + (RNG.nextFloat() * 1.0f);
        } else if (isCapture) {
            baseTime = 1.1f + (RNG.nextFloat() * 0.8f);
        } else {
            float eloFactor = Math.max(0.7f, 1.8f - (elo / 2200f));
            baseTime = (2.2f * eloFactor) + (RNG.nextFloat() * 2.0f);
        }
        return Math.round((baseTime + (RNG.nextFloat() * 0.35f)) * 100f) / 100f;
    }

    public static MoveDecision getBestMove(boolean isBlack, int moveCount, int elo) {
        // Данные: {Имя, Значок, Откуда, Куда}
        String[][] whitePool = {
            {"Пешка", "♙", "e2", "e4"},
            {"Пешка", "♙", "d2", "d4"},
            {"Конь", "♘", "g1", "f3"},
            {"Слон", "♗", "f1", "c4"},
            {"Пешка", "♙", "c2", "c4"},
            {"Конь", "♘", "b1", "c3"}
        };

        String[][] blackPool = {
            {"Пешка", "♟", "c7", "c5"},
            {"Пешка", "♟", "e7", "e5"},
            {"Конь", "♞", "g8", "f6"},
            {"Слон", "♝", "f8", "c5"},
            {"Пешка", "♟", "d7", "d5"},
            {"Конь", "♞", "b8", "c6"}
        };

        String[][] pool = isBlack ? blackPool : whitePool;
        String[] selected = pool[RNG.nextInt(pool.length)];

        boolean isCapture = RNG.nextFloat() > 0.8f;
        float delay = calculateThinkingTime(moveCount, isCapture, elo);

        return new MoveDecision(selected[0], selected[1], selected[2], selected[3], delay);
    }
}
