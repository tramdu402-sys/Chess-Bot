package com.chessbot;

import java.util.Random;

public class ChessEngine {
    private final int elo;
    private int step = 0;
    private final Random rng = new Random();

    public static class MoveDecision {
        public final String pieceName;
        public final String pieceIcon;
        public final String fromSquare;
        public final String toSquare;
        public final float thinkingSeconds;

        public MoveDecision(String pieceName, String pieceIcon, String fromSquare, String toSquare, float thinkingSeconds) {
            this.pieceName = pieceName;
            this.pieceIcon = pieceIcon;
            this.fromSquare = fromSquare;
            this.toSquare = toSquare;
            this.thinkingSeconds = thinkingSeconds;
        }
    }

    public ChessEngine(int elo) {
        this.elo = elo;
    }

    // Линейка ходов за Чёрных (Защита против 1.e4: e7-e5, Конь f6, Слон c5...)
    private static final String[][] BLACK_TREE = {
        {"Пешка", "♟", "e7", "e5"},
        {"Конь", "♞", "g8", "f6"},
        {"Слон", "♝", "f8", "c5"},
        {"Конь", "♞", "b8", "c6"},
        {"Пешка", "♟", "d7", "d6"},
        {"Король", "♚", "e8", "g8"},
        {"Слон", "♝", "c8", "g4"},
        {"Ферзь", "♛", "d8", "e7"}
    };

    // Линейка ходов за Белых (1.e4, Конь f3, Слон c4...)
    private static final String[][] WHITE_TREE = {
        {"Пешка", "♙", "e2", "e4"},
        {"Конь", "♘", "g1", "f3"},
        {"Слон", "♗", "f1", "c4"},
        {"Пешка", "♙", "d2", "d3"},
        {"Король", "♔", "e1", "g1"},
        {"Конь", "♘", "b1", "c3"},
        {"Слон", "♗", "c1", "e3"},
        {"Ферзь", "♕", "d1", "d2"}
    };

    private float getHumanDelay(int moveNum) {
        float base;
        if (moveNum <= 2) {
            base = 1.4f + (rng.nextFloat() * 0.8f);
        } else if (moveNum <= 6) {
            base = 2.2f + (rng.nextFloat() * 1.4f);
        } else {
            float eloFactor = Math.max(0.7f, 1.9f - (elo / 2100f));
            base = (2.8f * eloFactor) + (rng.nextFloat() * 2.2f);
        }
        return Math.max(1.2f, Math.round(base * 10f) / 10f);
    }

    public MoveDecision getNextMove(boolean isWhite) {
        String[][] tree = isWhite ? WHITE_TREE : BLACK_TREE;
        String[] m;

        if (step < tree.length) {
            m = tree[step];
        } else {
            String[] p = isWhite ? new String[]{"Конь", "Слон", "Ладья"} : new String[]{"Конь", "♝", "♜"};
            String[] ic = isWhite ? new String[]{"♘", "♗", "♖"} : new String[]{"♞", "♝", "♜"};
            int idx = rng.nextInt(p.length);
            m = new String[]{p[idx], ic[idx], isWhite ? "f3" : "f6", isWhite ? "e5" : "e4"};
        }

        float delay = getHumanDelay(step);
        step++;
        return new MoveDecision(m[0], m[1], m[2], m[3], delay);
    }
}
