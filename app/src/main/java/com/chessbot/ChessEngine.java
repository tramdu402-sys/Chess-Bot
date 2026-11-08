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

        // 1. Защита от прямого шаха
        if (inCheck && !isWhite) {
            return new EngineMove("Слон", "♗", "f8", "e7", 5, 7, 4, 6, 0.1f);
        }

        // 2. АНТИ-ЗЕВОК: Если белый ферзь вышел на h5 (enemyCol = 7, enemyRow = 4)
        if (!isWhite && enemyCol == 7 && enemyRow == 4) {
            // Защищаем пешку e5 конем с b8 на c6
            return new EngineMove("Конь", "♘", "b8", "c6", 1, 7, 2, 5, 0.4f);
        }
        
        // 3. АНТИ-ЗЕВОК 2: Если белый конь или слон лезут на c4 или f3
        if (!isWhite && (enemyCol == 2 && enemyRow == 3 || enemyCol == 5 && enemyRow == 5)) {
            return new EngineMove("Пешка", "♙", "g7", "g6", 6, 6, 6, 5, 0.5f);
        }

        // Базовое развитие
        if (!isWhite) {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♙", "e7", "e5", 4, 6, 4, 4, 0.8f);
                case 2: return new EngineMove("Конь", "♘", "g8", "f6", 6, 7, 5, 5, 0.9f);
                case 3: return new EngineMove("Слон", "♗", "f8", "c5", 5, 7, 2, 4, 1.0f);
                default: return new EngineMove("Король", "♔", "0-0", "0-0", 4, 7, 6, 7, 0.5f);
            }
        } else {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♙", "e2", "e4", 4, 6, 4, 4, 0.2f);
                case 2: return new EngineMove("Конь", "♘", "g1", "f3", 6, 7, 5, 5, 0.9f);
                default: return new EngineMove("Слон", "♗", "f1", "c4", 5, 7, 2, 4, 1.0f);
            }
        }
    }
}

