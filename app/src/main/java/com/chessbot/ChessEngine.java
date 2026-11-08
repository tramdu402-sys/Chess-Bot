package com.chessbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChessEngine {
    private final int elo;
    private int currentMoveIndex = 0;
    private final Random rng = new Random();
    private final List<String> playedHistory = new ArrayList<>();

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

    // Линейка ходов за Чёрных (Сицилианская защита / Найдорф)
    private static final String[][] BLACK_REPERTOIRE = {
        {"Пешка", "♟", "c7", "c5"},
        {"Пешка", "♟", "d7", "d6"},
        {"Конь", "♞", "g8", "f6"},
        {"Пешка", "♟", "a7", "a6"},
        {"Пешка", "♟", "e7", "e6"},
        {"Слон", "♝", "f8", "e7"},
        {"Король", "♚", "e8", "g8"},
        {"Конь", "♞", "b8", "c6"},
        {"Ферзь", "♛", "d8", "c7"},
        {"Ладья", "♜", "f8", "d8"},
        {"Слон", "♝", "c8", "d7"}
    };

    // Линейка ходов за Белых (Итальянская партия)
    private static final String[][] WHITE_REPERTOIRE = {
        {"Пешка", "♙", "e2", "e4"},
        {"Конь", "♘", "g1", "f3"},
        {"Слон", "♗", "f1", "c4"},
        {"Пешка", "♙", "d2", "d3"},
        {"Король", "♔", "e1", "g1"},
        {"Конь", "♘", "b1", "c3"},
        {"Пешка", "♙", "a2", "a4"},
        {"Слон", "♗", "c1", "e3"}
    };

    private float calculateHumanThinkingTime(int moveNum) {
        float base;
        if (moveNum <= 3) {
            // Начало: быстрый уверенный ответ (1.8 - 2.6 сек)
            base = 1.8f + (rng.nextFloat() * 0.8f);
        } else if (moveNum <= 8) {
            // Развитие: среднее время (2.8 - 4.2 сек)
            base = 2.8f + (rng.nextFloat() * 1.4f);
        } else {
            // Миттельшпиль: время зависит от выбранного ELO
            float eloFactor = Math.max(0.7f, 1.9f - (elo / 2100f));
            base = (3.2f * eloFactor) + (rng.nextFloat() * 2.8f);
        }
        float jitter = (rng.nextFloat() * 0.4f) - 0.2f;
        return Math.max(1.3f, Math.round((base + jitter) * 100f) / 100f);
    }

    public MoveDecision getNextMove(boolean isWhite) {
        String[][] repertoire = isWhite ? WHITE_REPERTOIRE : BLACK_REPERTOIRE;
        String[] moveData;

        if (currentMoveIndex < repertoire.length) {
            moveData = repertoire[currentMoveIndex];
        } else {
            String[] pieces = isWhite ? new String[]{"Конь", "Слон", "Ладья", "Ферзь"} : new String[]{"Конь", "Слон", "Ладья", "Ферзь"};
            String[] icons = isWhite ? new String[]{"♘", "♗", "♖", "♕"} : new String[]{"♞", "♝", "♜", "♛"};
            int pick = rng.nextInt(pieces.length);
            moveData = new String[]{pieces[pick], icons[pick], isWhite ? "f3" : "f6", isWhite ? "e5" : "e4"};
        }

        float delay = calculateHumanThinkingTime(currentMoveIndex);
        playedHistory.add(moveData[2] + "-" + moveData[3]);
        currentMoveIndex++;

        return new MoveDecision(moveData[0], moveData[1], moveData[2], moveData[3], delay);
    }
}
