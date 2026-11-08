package com.chessbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ChessEngine {

    public static class EngineMove {
        public final int move;
        public final String pieceName;
        public final String pieceIcon;
        public final String fromCoord;
        public final String toCoord;
        public final int from, to;

        public EngineMove(int move, String name, String icon, String fromCoord, String toCoord, int from, int to) {
            this.move = move; this.pieceName = name; this.pieceIcon = icon;
            this.fromCoord = fromCoord; this.toCoord = toCoord;
            this.from = from; this.to = to;
        }
    }

    private static final String[] NAMES = {"", "Пешка", "Конь", "Слон", "Ладья", "Ферзь", "Король"};
    private static final String[] ICONS = {"", "♙", "♘", "♗", "♖", "♕", "♔"};

    private static final int INF = 1_000_000;
    private static final int MATE = 100_000;
    private static final int[] VAL = {0, 100, 320, 330, 500, 900, 0};

    private final int elo;
    private final Random rnd = new Random();

    private Position pos = Position.start();
    private final HashMap<String, Integer> hist = new HashMap<>();
    private int moveCount = 0;

    private volatile boolean cancel = false;
    private long nodes, deadline;
    private boolean aborted;

    public ChessEngine(int elo) {
        this.elo = elo;
        newGame();
    }

    public void newGame() {
        pos = Position.start();
        hist.clear();
        hist.put(pos.key(), 1);
        moveCount = 0;
    }

    public int sideToMove() { return pos.side; }
    public int moveCount() { return moveCount; }
    public Position position() { return pos; }
    public Position snapshot() { return pos.copy(); }
    public HashMap<String, Integer> historySnapshot() { return new HashMap<>(hist); }
    public void cancel() { cancel = true; }

    public boolean isGameOver() {
        int[] mv = new int[256];
        return pos.legalMoves(mv) == 0 || pos.halfmove >= 100;
    }

    public void apply(int move) {
        pos = pos.make(move);
        hist.merge(pos.key(), 1, Integer::sum);
        moveCount++;
    }

    public List<Integer> candidates(int[] litSquares) {
        int[] mv = new int[256];
        int n = pos.legalMoves(mv);
        List<Integer> res = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int m = mv[i];
            int from = Position.from(m), to = Position.to(m), pr = Position.promo(m);
            if (pr != 0 && pr != Position.Q) continue;
            boolean hasFrom = contains(litSquares, from);
            boolean match = hasFrom && contains(litSquares, to);
            if (!match && (m & Position.F_CASTLE_K) != 0) match = hasFrom && contains(litSquares, to + 1);
            if (!match && (m & Position.F_CASTLE_Q) != 0) match = hasFrom && contains(litSquares, to - 2);
            if (match) res.add(m);
        }
        return res;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    public static EngineMove describe(Position p, int m) {
        int from = Position.from(m), to = Position.to(m);
        int t = Position.type(p.b[from]);
        String name = NAMES[t];
        if ((m & Position.F_CASTLE_K) != 0) name = "Король · 0-0";
        else if ((m & Position.F_CASTLE_Q) != 0) name = "Король · 0-0-0";
        else if (Position.promo(m) != 0) name = "Пешка → " + NAMES[Position.promo(m)];
        return new EngineMove(m, name, ICONS[t], Position.sqName(from), Position.sqName(to), from, to);
    }

    private int maxDepth() {
        if (elo < 600) return 1;
        if (elo < 900) return 2;
        if (elo < 1300) return 3;
        if (elo < 1700) return 4;
        if (elo < 2100) return 5;
        return 8;
    }

    private long timeLimitMs() {
        if (elo < 1000) return 600;
        if (elo < 1800) return 1200;
        if (elo < 2400) return 2500;
        return 4000;
    }

    private int marginCp() {
        if (elo >= 2000) return 0;
        return (2000 - elo) / 4;
    }

    public EngineMove bestMove(Position p, Map<String, Integer> history) {
        cancel = false;
        aborted = false;
        nodes = 0;
        deadline = System.currentTimeMillis() + timeLimitMs();

        int[] mv = new int[256];
        int n = p.legalMoves(mv);
        if (n == 0) return null;
        if (n == 1) return describe(p, mv[0]);

        int[] scores = new int[n];
        int[] done = null;
        int depthCap = maxDepth();

        boolean[] repeat = new boolean[n];
        for (int i = 0; i < n; i++) {
            Integer c = history.get(p.make(mv[i]).key());
            repeat[i] = c != null && c >= 2;
        }

        for (int d = 1; d <= depthCap; d++) {
            int[] cur = new int[n];
            int best = -INF;
            for (int i = 0; i < n; i++) {
                int s;
                if (repeat[i]) {
                    s = 0;
                } else {
                    Position child = p.make(mv[i]);
                    if (marginCp() > 0) {
                        s = -search(child, d - 1, -INF, INF, 1);
                    } else {
                        s = -search(child, d - 1, -INF, -best + 1, 1);
                    }
                }
                if (aborted) break;
                cur[i] = s;
                if (s > best) best = s;
            }
            if (aborted) break;
            done = cur;
            int bi = 0;
            for (int i = 1; i < n; i++) if (cur[i] > cur[bi]) bi = i;
            swap(mv, 0, bi); swap(cur, 0, bi); swap(repeat, 0, bi);
            done = cur;
            if (best > MATE - 100) break;
        }
        if (done == null) done = scores;
        System.arraycopy(done, 0, scores, 0, n);

        int best = -INF;
        for (int i = 0; i < n; i++) if (scores[i] > best) best = scores[i];
        int margin = best > MATE - 100 ? 0 : marginCp();
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) if (scores[i] >= best - margin) pool.add(i);
        int pick = pool.get(pool.size() == 1 ? 0 : rnd.nextInt(pool.size()));
        if (margin == 0) {
            for (int i = 0; i < n; i++) if (scores[i] == best) { pick = i; break; }
        }
        return describe(p, mv[pick]);
    }

    private static void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
    private static void swap(boolean[] a, int i, int j) { boolean t = a[i]; a[i] = a[j]; a[j] = t; }

    private boolean timeUp() {
        if ((++nodes & 2047) == 0) {
            if (cancel || System.currentTimeMillis() > deadline) aborted = true;
        }
        return aborted;
    }

    private int search(Position p, int depth, int alpha, int beta, int ply) {
        if (timeUp()) return 0;
        boolean check = p.inCheck(p.side);
        if (check && depth < 1 && ply < 12) depth = 1;
        if (depth <= 0) return quiesce(p, alpha, beta, 0);

        int[] mv = new int[256];
        int n = p.legalMoves(mv);
        if (n == 0) return check ? -MATE + ply : 0;
        if (p.halfmove >= 100) return 0;

        int[] sc = new int[n];
        for (int i = 0; i < n; i++) sc[i] = orderScore(p, mv[i]);

        int best = -INF;
        for (int i = 0; i < n; i++) {
            int bi = i;
            for (int j = i + 1; j < n; j++) if (sc[j] > sc[bi]) bi = j;
            swap(mv, i, bi); swap(sc, i, bi);
            int s = -search(p.make(mv[i]), depth - 1, -beta, -alpha, ply + 1);
            if (aborted) return 0;
            if (s > best) best = s;
            if (s > alpha) alpha = s;
            if (alpha >= beta) break;
        }
        return best;
    }

    private int quiesce(Position p, int alpha, int beta, int qd) {
        if (timeUp()) return 0;
        int stand = evaluate(p);
        if (stand >= beta) return stand;
        if (stand > alpha) alpha = stand;
        if (qd >= 6) return alpha;

        int[] mv = new int[256];
        int n = p.legalMoves(mv);
        int[] cap = new int[n];
        int cn = 0;
        int[] sc = new int[n];
        for (int i = 0; i < n; i++) {
            int m = mv[i];
            boolean isCap = p.b[Position.to(m)] != 0 || (m & Position.F_EP) != 0;
            if (isCap || Position.promo(m) == Position.Q) {
                cap[cn] = m;
                sc[cn] = orderScore(p, m);
                cn++;
            }
        }
        for (int i = 0; i < cn; i++) {
            int bi = i;
            for (int j = i + 1; j < cn; j++) if (sc[j] > sc[bi]) bi = j;
            swap(cap, i, bi); swap(sc, i, bi);
            int s = -quiesce(p.make(cap[i]), -beta, -alpha, qd + 1);
            if (aborted) return 0;
            if (s >= beta) return s;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    private int orderScore(Position p, int m) {
        int from = Position.from(m), to = Position.to(m);
        int victim = Position.type(p.b[to]);
        int attacker = Position.type(p.b[from]);
        int s = 0;
        if (victim != 0) s += 10 * VAL[victim] - VAL[attacker] + 10_000;
        else if ((m & Position.F_EP) != 0) s += 10_000 + 900;
        if (Position.promo(m) == Position.Q) s += 9_000;
        return s;
    }

    private static final int[][] PST = {
        null,
        {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0},
        {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50},
        {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20},
        {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0},
        {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20},
        {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20}
    };

    private static final int[] KING_END = {
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50};

    static int evaluate(Position p) {
        int nonPawn = 0;
        for (int s = 0; s < 64; s++) {
            int t = p.b[s] & 7;
            if (t >= 2 && t <= 5) nonPawn += VAL[t];
        }
        boolean endgame = nonPawn < 2600;
        int score = 0;
        for (int s = 0; s < 64; s++) {
            int pc = p.b[s];
            if (pc == 0) continue;
            int t = pc & 7, col = pc >> 3;
            int idx = col == Position.WHITE ? (s ^ 56) : s;
            int v = VAL[t];
            if (t == Position.K) v = endgame ? KING_END[idx] : PST[t][idx];
            else v += PST[t][idx];
            score += col == Position.WHITE ? v : -v;
        }
        return p.side == Position.WHITE ? score : -score;
    }
}