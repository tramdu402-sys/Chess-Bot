package com.chessbot;

public class ChessEngine {
    private final int elo;
    private int step = 0;

    public static class EngineMove {
        public final String pieceName;
        public final String pieceIcon;
        public final String fromCoord;
        public final String toCoord;
        public final int fromCol, fromRow;
        public final int toCol, toRow;
        public final float delaySec;

        public EngineMove(String name, String icon, String from, String to, int fC, int fR, int tC, int tR, float sec) {
            this.pieceName = name; this.pieceIcon = icon;
            this.fromCoord = from; this.toCoord = to;
            this.fromCol = fC; this.fromRow = fR;
            this.toCol = tC; this.toRow = tR;
            this.delaySec = sec;
        }
    }

    public ChessEngine(int elo) {
        this.elo = elo;
    }

    public EngineMove findMove(boolean isWhite, int enemyCol, int enemyRow, boolean inCheck) {
        step++;

        // СИТУАЦИЯ СО СКРИНШОТА: Если нам объявили шах (Красный король e8)
        if (inCheck && !isWhite) {
            // Мгновенная защита от шаха слоном: Слон f8 -> e7
            return new EngineMove("Слон", "♝", "f8", "e7", 5, 7, 4, 6, 0.1f);
        }

        // Защита ферзя от слона Bg5 (h7 -> h6)
        if (!isWhite && (enemyCol == 1 && enemyRow == 4)) {
            return new EngineMove("Пешка", "♟", "h7", "h6", 0, 6, 0, 5, 0.15f);
        }

        // Взятие врага, если он вторгся слишком глубоко (на c7)
        if (!isWhite && enemyRow >= 6) {
            return new EngineMove("Ферзь", "♛", "d8", "c7", 4, 7, enemyCol, enemyRow, 0.15f);
        }

        // Базовое развитие
        if (!isWhite) {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♟", "e7", "e5", 3, 6, 3, 4, 0.8f);
                case 2: return new EngineMove("Конь", "♞", "g8", "f6", 1, 7, 2, 5, 0.9f);
                case 3: return new EngineMove("Конь", "♞", "b8", "c6", 6, 7, 5, 5, 0.9f);
                case 4: return new EngineMove("Слон", "♝", "f8", "c5", 2, 7, 5, 4, 1.0f);
                default: return new EngineMove("Король", "♚", "0-0", "0-0", 4, 7, 6, 7, 0.5f);
            }
        } else {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♙", "e2", "e4", 4, 6, 4, 4, 0.8f);
                case 2: return new EngineMove("Конь", "♘", "g1", "f3", 6, 7, 5, 5, 0.9f);
                default: return new EngineMove("Слон", "♗", "f1", "c4", 5, 7, 2, 4, 1.0f);
            }
        }
    }
}
