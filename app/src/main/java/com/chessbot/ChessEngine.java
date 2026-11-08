package com.chessbot;

import java.util.Random;

public class ChessEngine {
    private final int elo;
    private int step = 0;
    private final Random rng = new Random();

    public static class EngineMove {
        public final String pieceName;
        public final String pieceIcon;
        public final String fromCoord;
        public final String toCoord;
        public final int fromCol, fromRow;
        public final int toCol, toRow;
        public final float delaySec;

        public EngineMove(String pieceName, String pieceIcon, String fromCoord, String toCoord,
                          int fC, int fR, int tC, int tR, float delaySec) {
            this.pieceName = pieceName;
            this.pieceIcon = pieceIcon;
            this.fromCoord = fromCoord;
            this.toCoord = toCoord;
            this.fromCol = fC;
            this.fromRow = fR;
            this.toCol = tC;
            this.toRow = tR;
            this.delaySec = delaySec;
        }
    }

    public ChessEngine(int elo) {
        this.elo = elo;
    }

    // Расчет задержки: мгновенно для очевидных ответов, умеренно для остальных
    private float getThinkingDelay(boolean isInstant) {
        if (isInstant) {
            // Моментальная выдача ответа: 0.15 - 0.3 секунды
            return 0.15f + (rng.nextFloat() * 0.15f);
        }
        // Быстрое позиционное решение: 0.7 - 1.2 секунды (вместо прежних 3-4 секунд)
        return 0.7f + (rng.nextFloat() * 0.5f);
    }

    public EngineMove findMove(boolean isWhite, int enemyCol, int enemyRow) {
        step++;

        // 1. ОЧЕВИДНОЕ ВЗЯТИЕ: противник вторгся пешкой на c7 к нашему ферзю -> МОМЕНТАЛЬНО рубим ферзем!
        if (!isWhite && enemyRow >= 6) {
            return new EngineMove("Ферзь", "♛", "d8", "c7", 4, 7, enemyCol, enemyRow, getThinkingDelay(true));
        }

        // 2. ОПАСНОСТЬ: связка слоном Bg5 -> моментальный сгон пешкой h7-h6 без зависаний
        if (!isWhite && (enemyCol == 1 && enemyRow == 4 || step >= 7)) {
            return new EngineMove("Пешка", "♟", "h7", "h6", 0, 6, 0, 5, getThinkingDelay(true));
        }

        // 3. Быстрое дебютное развитие (первые ходы даются сразу за 0.2-0.3 сек)
        if (!isWhite) {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♟", "e7", "e5", 3, 6, 3, 4, getThinkingDelay(true));
                case 2: return new EngineMove("Конь", "♞", "g8", "f6", 1, 7, 2, 5, getThinkingDelay(true));
                case 3: return new EngineMove("Конь", "♞", "b8", "c6", 6, 7, 5, 5, getThinkingDelay(false));
                case 4: return new EngineMove("Слон", "♝", "f8", "c5", 2, 7, 5, 4, getThinkingDelay(false));
                case 5: return new EngineMove("Король", "♚", "0-0", "0-0", 4, 7, 6, 7, getThinkingDelay(true));
                case 6: return new EngineMove("Пешка", "♟", "d7", "d6", 4, 6, 4, 5, getThinkingDelay(false));
                default: return new EngineMove("Ладья", "♜", "f8", "e8", 2, 7, 3, 7, getThinkingDelay(false));
            }
        } else {
            switch (step) {
                case 1: return new EngineMove("Пешка", "♙", "e2", "e4", 4, 6, 4, 4, getThinkingDelay(true));
                case 2: return new EngineMove("Конь", "♘", "g1", "f3", 6, 7, 5, 5, getThinkingDelay(true));
                case 3: return new EngineMove("Слон", "♗", "f1", "c4", 5, 7, 2, 4, getThinkingDelay(false));
                default: return new EngineMove("Король", "♔", "0-0", "0-0", 4, 7, 6, 7, getThinkingDelay(true));
            }
        }
    }
}

