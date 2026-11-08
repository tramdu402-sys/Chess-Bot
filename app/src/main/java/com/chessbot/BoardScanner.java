package com.chessbot;

import java.util.Arrays;

public final class BoardScanner {

    public interface Frame {
        int width();
        int height();
        int rgb(int x, int y);
    }

    public static final class Obs {
        public final boolean[] lit = new boolean[64];
        public final boolean[] occ = new boolean[64];
        public final boolean[] white = new boolean[64];

        public long litMask() {
            long m = 0;
            for (int i = 0; i < 64; i++) if (lit[i]) m |= 1L << i;
            return m;
        }
    }

    private static int dist(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
             + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
             + Math.abs((a & 255) - (b & 255));
    }

    private static int channelMedian(int[] v, int n, int shift) {
        int[] c = new int[n];
        for (int i = 0; i < n; i++) c[i] = (v[i] >> shift) & 255;
        Arrays.sort(c);
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2;
    }

    private static int medianColor(int[] v, int n) {
        return (channelMedian(v, n, 16) << 16) | (channelMedian(v, n, 8) << 8) | channelMedian(v, n, 0);
    }

    private static int luma(int c) {
        return (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000;
    }

    static boolean boardPattern(Frame f, int y0) {
        int w = f.width(), h = f.height();
        int sq = w / 8;
        if (sq < 8 || y0 < 0 || y0 + 8 * sq > h) return false;
        int[] medA = new int[8], medB = new int[8];
        for (int r = 0; r < 8; r++) {
            int y = y0 + r * sq + (int) (0.55f * sq);
            int[] a = new int[4], b = new int[4];
            for (int i = 0; i < 8; i++) {
                int c = f.rgb(i * sq + (int) (0.12f * sq), y);
                if ((i & 1) == 0) a[i / 2] = c; else b[i / 2] = c;
            }
            int mA = medianColor(a, 4), mB = medianColor(b, 4);
            int okA = 0, okB = 0;
            for (int i = 0; i < 4; i++) {
                if (dist(a[i], mA) <= 30) okA++;
                if (dist(b[i], mB) <= 30) okB++;
            }
            if (okA < 3 || okB < 3) return false;
            if (dist(mA, mB) < 30) return false;
            medA[r] = mA; medB[r] = mB;
        }
        for (int r = 0; r < 7; r++) {
            if (dist(medA[r + 1], medB[r]) > 36) return false;
            if (dist(medB[r + 1], medA[r]) > 36) return false;
        }
        return true;
    }

    public int detectBoard(Frame f) {
        int w = f.width(), h = f.height();
        int sq = w / 8;
        int from = (int) (0.04f * h), to = h - 8 * sq;
        int runStart = -1, last = -1;
        for (int y0 = from; y0 <= to; y0 += 2) {
            if (boardPattern(f, y0)) {
                if (runStart < 0) runStart = y0;
                last = y0;
            } else if (runStart >= 0) {
                break;
            }
        }
        if (runStart < 0) return -1;
        if (last - runStart < (int) (0.3f * sq)) return -1;
        return (runStart + last) / 2 + (int) (0.05f * sq);
    }

    public boolean verifyBoard(Frame f, int top) {
        return boardPattern(f, top);
    }

    private static final float[][] BASE_PTS = {{0.10f, 0.50f}, {0.90f, 0.50f}, {0.90f, 0.10f}};

    public Obs observe(Frame f, int top) {
        int w = f.width();
        int sq = w / 8;
        int[][] base = new int[64][3];
        int[] par0 = new int[32 * 3], par1 = new int[32 * 3];
        int n0 = 0, n1 = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int idx = row * 8 + col;
                for (int k = 0; k < 3; k++) {
                    int x = col * sq + (int) (BASE_PTS[k][0] * sq);
                    int y = top + row * sq + (int) (BASE_PTS[k][1] * sq);
                    int c = f.rgb(x, y);
                    base[idx][k] = c;
                    if (((row + col) & 1) == 0) par0[n0++] = c; else par1[n1++] = c;
                }
            }
        }
        int med0 = medianColor(par0, n0), med1 = medianColor(par1, n1);

        Obs o = new Obs();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int idx = row * 8 + col;
                int med = ((row + col) & 1) == 0 ? med0 : med1;
                int bestC = base[idx][0], bestD = dist(bestC, med);
                for (int k = 1; k < 3; k++) {
                    int d = dist(base[idx][k], med);
                    if (d < bestD) { bestD = d; bestC = base[idx][k]; }
                }
                o.lit[idx] = bestD > 35;

                int diffCnt = 0, lumSum = 0;
                for (int dy = 0; dy < 3; dy++) {
                    for (int dx = 0; dx < 3; dx++) {
                        float fx = 0.40f + 0.10f * dx, fy = 0.45f + 0.15f * dy;
                        int c = f.rgb(col * sq + (int) (fx * sq), top + row * sq + (int) (fy * sq));
                        if (dist(c, bestC) > 35) { diffCnt++; lumSum += luma(c); }
                    }
                }
                o.occ[idx] = diffCnt >= 3;
                o.white[idx] = o.occ[idx] && (lumSum / diffCnt) > 135;
            }
        }
        return o;
    }

    public static boolean isStartPosition(Obs o) {
        int errors = 0, litCount = 0;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int i = row * 8 + col;
                boolean shouldBeOcc = row <= 1 || row >= 6;
                if (o.occ[i] != shouldBeOcc) errors++;
                if (o.lit[i]) litCount++;
            }
        }
        if (errors > 4 || litCount > 0) return false;
        int bottomWhite = 0, topWhite = 0;
        for (int col = 0; col < 8; col++) {
            for (int row = 6; row <= 7; row++) if (o.occ[row * 8 + col] && o.white[row * 8 + col]) bottomWhite++;
            for (int row = 0; row <= 1; row++) if (o.occ[row * 8 + col] && o.white[row * 8 + col]) topWhite++;
        }
        boolean bottomIsWhite = bottomWhite >= 12 && topWhite <= 4;
        boolean bottomIsBlack = bottomWhite <= 4 && topWhite >= 12;
        return bottomIsWhite || bottomIsBlack;
    }

    public static boolean bottomIsWhite(Obs o) {
        int bottomWhite = 0;
        for (int col = 0; col < 8; col++)
            for (int row = 6; row <= 7; row++)
                if (o.occ[row * 8 + col] && o.white[row * 8 + col]) bottomWhite++;
        return bottomWhite >= 8;
    }

    public static int screenToSquare(int screenIdx, boolean whiteAtBottom) {
        int row = screenIdx / 8, col = screenIdx % 8;
        return whiteAtBottom ? (7 - row) * 8 + col : row * 8 + (7 - col);
    }

    public static int squareToScreen(int sq, boolean whiteAtBottom) {
        int file = sq & 7, rank = sq >> 3;
        return whiteAtBottom ? (7 - rank) * 8 + file : rank * 8 + (7 - file);
    }
}