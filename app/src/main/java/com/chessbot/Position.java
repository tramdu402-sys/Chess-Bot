package com.chessbot;

public final class Position {
    public static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;
    public static final int WHITE = 0, BLACK = 1;

    public static final int F_DOUBLE = 1 << 16;
    public static final int F_EP = 1 << 17;
    public static final int F_CASTLE_K = 1 << 18;
    public static final int F_CASTLE_Q = 1 << 19;
    public static final int FLAGS_MASK = 0xF << 16;

    public final byte[] b = new byte[64];
    public int side = WHITE;
    public int castle = 15;
    public int ep = -1;
    public int halfmove = 0;
    public int fullmove = 1;
    public final int[] king = new int[2];

    private static final int[] KN_DF = {1, 2, 2, 1, -1, -2, -2, -1};
    private static final int[] KN_DR = {2, 1, -1, -2, -2, -1, 1, 2};
    private static final int[] K_DF = {1, 1, 1, 0, -1, -1, -1, 0};
    private static final int[] K_DR = {1, 0, -1, -1, -1, 0, 1, 1};
    private static final int[] DIAG_DF = {1, 1, -1, -1};
    private static final int[] DIAG_DR = {1, -1, 1, -1};
    private static final int[] ORTH_DF = {1, -1, 0, 0};
    private static final int[] ORTH_DR = {0, 0, 1, -1};

    public static int from(int m) { return m & 63; }
    public static int to(int m) { return (m >> 6) & 63; }
    public static int promo(int m) { return (m >> 12) & 15; }
    public static int color(int piece) { return piece >> 3; }
    public static int type(int piece) { return piece & 7; }

    public static Position start() {
        return fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public static Position fromFen(String fen) {
        Position p = new Position();
        String[] parts = fen.trim().split("\\s+");
        String[] ranks = parts[0].split("/");
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i, file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) { file += c - '0'; continue; }
                int t = "pnbrqk".indexOf(Character.toLowerCase(c)) + 1;
                int col = Character.isUpperCase(c) ? WHITE : BLACK;
                int sq = rank * 8 + file;
                p.b[sq] = (byte) ((col << 3) | t);
                if (t == K) p.king[col] = sq;
                file++;
            }
        }
        p.side = parts.length > 1 && parts[1].equals("b") ? BLACK : WHITE;
        p.castle = 0;
        if (parts.length > 2) {
            for (char c : parts[2].toCharArray()) {
                if (c == 'K') p.castle |= 1;
                else if (c == 'Q') p.castle |= 2;
                else if (c == 'k') p.castle |= 4;
                else if (c == 'q') p.castle |= 8;
            }
        }
        p.ep = -1;
        if (parts.length > 3 && !parts[3].equals("-")) {
            p.ep = (parts[3].charAt(1) - '1') * 8 + (parts[3].charAt(0) - 'a');
        }
        if (parts.length > 4) p.halfmove = Integer.parseInt(parts[4]);
        if (parts.length > 5) p.fullmove = Integer.parseInt(parts[5]);
        return p;
    }

    public Position copy() {
        Position p = new Position();
        System.arraycopy(b, 0, p.b, 0, 64);
        p.side = side; p.castle = castle; p.ep = ep;
        p.halfmove = halfmove; p.fullmove = fullmove;
        p.king[0] = king[0]; p.king[1] = king[1];
        return p;
    }

    public String key() {
        char[] c = new char[67];
        for (int i = 0; i < 64; i++) c[i] = (char) ('A' + b[i]);
        c[64] = (char) ('0' + side);
        c[65] = (char) ('A' + castle);
        c[66] = (char) ('A' + (ep < 0 ? 0 : ep % 8 + 1));
        return new String(c);
    }

    public static String sqName(int s) {
        return "" + (char) ('a' + (s & 7)) + (char) ('1' + (s >> 3));
    }

    public boolean attacked(int sq, int by) {
        int f = sq & 7, r = sq >> 3;
        int pr = by == WHITE ? r - 1 : r + 1;
        if (pr >= 0 && pr <= 7) {
            int pawn = (by << 3) | P;
            if (f > 0 && b[pr * 8 + f - 1] == pawn) return true;
            if (f < 7 && b[pr * 8 + f + 1] == pawn) return true;
        }
        int knight = (by << 3) | N;
        for (int i = 0; i < 8; i++) {
            int nf = f + KN_DF[i], nr = r + KN_DR[i];
            if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
            if (b[nr * 8 + nf] == knight) return true;
        }
        int kg = (by << 3) | K;
        for (int i = 0; i < 8; i++) {
            int nf = f + K_DF[i], nr = r + K_DR[i];
            if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
            if (b[nr * 8 + nf] == kg) return true;
        }
        int bishop = (by << 3) | B, rook = (by << 3) | R, queen = (by << 3) | Q;
        for (int d = 0; d < 4; d++) {
            int nf = f + DIAG_DF[d], nr = r + DIAG_DR[d];
            while (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
                int p = b[nr * 8 + nf];
                if (p != 0) { if (p == bishop || p == queen) return true; break; }
                nf += DIAG_DF[d]; nr += DIAG_DR[d];
            }
            nf = f + ORTH_DF[d]; nr = r + ORTH_DR[d];
            while (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
                int p = b[nr * 8 + nf];
                if (p != 0) { if (p == rook || p == queen) return true; break; }
                nf += ORTH_DF[d]; nr += ORTH_DR[d];
            }
        }
        return false;
    }

    public boolean inCheck(int color) {
        return attacked(king[color], color ^ 1);
    }

    private int addPawn(int[] out, int n, int from, int to, boolean promo) {
        if (promo) {
            out[n++] = from | (to << 6) | (Q << 12);
            out[n++] = from | (to << 6) | (R << 12);
            out[n++] = from | (to << 6) | (B << 12);
            out[n++] = from | (to << 6) | (N << 12);
        } else {
            out[n++] = from | (to << 6);
        }
        return n;
    }

    private int pseudo(int[] out) {
        int n = 0;
        final int us = side, them = side ^ 1;
        for (int s = 0; s < 64; s++) {
            int p = b[s];
            if (p == 0 || (p >> 3) != us) continue;
            int t = p & 7, f = s & 7, r = s >> 3;
            switch (t) {
                case P: {
                    int dir = us == WHITE ? 1 : -1;
                    int nr = r + dir;
                    if (nr < 0 || nr > 7) break;
                    boolean promo = (nr == 7 || nr == 0);
                    int to = nr * 8 + f;
                    if (b[to] == 0) {
                        n = addPawn(out, n, s, to, promo);
                        if ((us == WHITE && r == 1) || (us == BLACK && r == 6)) {
                            int to2 = (r + 2 * dir) * 8 + f;
                            if (b[to2] == 0) out[n++] = s | (to2 << 6) | F_DOUBLE;
                        }
                    }
                    for (int df = -1; df <= 1; df += 2) {
                        int nf = f + df;
                        if (nf < 0 || nf > 7) continue;
                        int tt = nr * 8 + nf;
                        if (b[tt] != 0 && (b[tt] >> 3) == them) n = addPawn(out, n, s, tt, promo);
                        else if (b[tt] == 0 && tt == ep) out[n++] = s | (tt << 6) | F_EP;
                    }
                    break;
                }
                case N: {
                    for (int i = 0; i < 8; i++) {
                        int nf = f + KN_DF[i], nr = r + KN_DR[i];
                        if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
                        int tt = nr * 8 + nf;
                        if (b[tt] == 0 || (b[tt] >> 3) == them) out[n++] = s | (tt << 6);
                    }
                    break;
                }
                case K: {
                    for (int i = 0; i < 8; i++) {
                        int nf = f + K_DF[i], nr = r + K_DR[i];
                        if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
                        int tt = nr * 8 + nf;
                        if (b[tt] == 0 || (b[tt] >> 3) == them) out[n++] = s | (tt << 6);
                    }
                    if (us == WHITE && s == 4) {
                        if ((castle & 1) != 0 && b[5] == 0 && b[6] == 0 && b[7] == ((WHITE << 3) | R)
                                && !attacked(4, BLACK) && !attacked(5, BLACK))
                            out[n++] = 4 | (6 << 6) | F_CASTLE_K;
                        if ((castle & 2) != 0 && b[3] == 0 && b[2] == 0 && b[1] == 0 && b[0] == ((WHITE << 3) | R)
                                && !attacked(4, BLACK) && !attacked(3, BLACK))
                            out[n++] = 4 | (2 << 6) | F_CASTLE_Q;
                    } else if (us == BLACK && s == 60) {
                        if ((castle & 4) != 0 && b[61] == 0 && b[62] == 0 && b[63] == ((BLACK << 3) | R)
                                && !attacked(60, WHITE) && !attacked(61, WHITE))
                            out[n++] = 60 | (62 << 6) | F_CASTLE_K;
                        if ((castle & 8) != 0 && b[59] == 0 && b[58] == 0 && b[57] == 0 && b[56] == ((BLACK << 3) | R)
                                && !attacked(60, WHITE) && !attacked(59, WHITE))
                            out[n++] = 60 | (58 << 6) | F_CASTLE_Q;
                    }
                    break;
                }
                default: {
                    boolean diag = (t == B || t == Q), orth = (t == R || t == Q);
                    for (int d = 0; d < 4; d++) {
                        for (int pass = 0; pass < 2; pass++) {
                            if (pass == 0 && !diag) continue;
                            if (pass == 1 && !orth) continue;
                            int df = pass == 0 ? DIAG_DF[d] : ORTH_DF[d];
                            int dr = pass == 0 ? DIAG_DR[d] : ORTH_DR[d];
                            int nf = f + df, nr = r + dr;
                            while (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
                                int tt = nr * 8 + nf;
                                if (b[tt] == 0) {
                                    out[n++] = s | (tt << 6);
                                } else {
                                    if ((b[tt] >> 3) == them) out[n++] = s | (tt << 6);
                                    break;
                                }
                                nf += df; nr += dr;
                            }
                        }
                    }
                }
            }
        }
        return n;
    }

    public int legalMoves(int[] out) {
        int[] ps = new int[256];
        int n = pseudo(ps);
        int c = 0;
        for (int i = 0; i < n; i++) {
            Position q = make(ps[i]);
            if (!q.attacked(q.king[side], side ^ 1)) out[c++] = ps[i];
        }
        return c;
    }

    public Position make(int m) {
        Position q = copy();
        int from = m & 63, to = (m >> 6) & 63, promo = (m >> 12) & 15, flags = m & FLAGS_MASK;
        int us = side;
        int p = q.b[from];
        int type = p & 7;
        boolean capture = q.b[to] != 0;
        q.b[from] = 0;
        if ((flags & F_EP) != 0) {
            q.b[to + (us == WHITE ? -8 : 8)] = 0;
            capture = true;
        }
        q.b[to] = (byte) (promo != 0 ? ((us << 3) | promo) : p);
        if (type == K) q.king[us] = to;
        if ((flags & F_CASTLE_K) != 0) { q.b[to - 1] = q.b[to + 1]; q.b[to + 1] = 0; }
        if ((flags & F_CASTLE_Q) != 0) { q.b[to + 1] = q.b[to - 2]; q.b[to - 2] = 0; }

        if (type == K) q.castle &= (us == WHITE) ? ~3 : ~12;
        if (from == 0 || to == 0) q.castle &= ~2;
        if (from == 7 || to == 7) q.castle &= ~1;
        if (from == 56 || to == 56) q.castle &= ~8;
        if (from == 63 || to == 63) q.castle &= ~4;

        q.ep = ((flags & F_DOUBLE) != 0) ? (from + to) / 2 : -1;
        q.halfmove = (type == P || capture) ? 0 : halfmove + 1;
        q.side = us ^ 1;
        if (us == BLACK) q.fullmove = fullmove + 1;
        return q;
    }

    public static long perft(Position p, int depth) {
        if (depth == 0) return 1;
        int[] mv = new int[256];
        int n = p.legalMoves(mv);
        if (depth == 1) return n;
        long total = 0;
        for (int i = 0; i < n; i++) total += perft(p.make(mv[i]), depth - 1);
        return total;
    }
}