package server.model;

import common.model.BoardState;
import common.model.Wall;

import java.util.*;

/**
 * Logic ban co 9x9 kieu Quoridor.
 * - Toa do o: row,col trong [0..8].
 * - Toa do wall: row,col trong [0..7] (goc tren-trai cua khoi 2x2 ma wall do chan),
 *   orientation "H" (ngang) hoac "V" (doc). Moi wall chan 2 canh lien tiep.
 * - Player1 xuat phat hang 0, dich la hang 8. Player2 xuat phat hang 8, dich la hang 0.
 */
public class Board {
    public static final int SIZE = 9;
    public static final int START_BARRICADES = 10;

    public int p1Row = 0, p1Col = 4;
    public int p2Row = 8, p2Col = 4;
    public int p1BarricadesLeft = START_BARRICADES;
    public int p2BarricadesLeft = START_BARRICADES;

    private final Set<Wall> walls = new HashSet<>();

    // ---------- DI CHUYEN ----------

    public boolean isAdjacent(int r1, int c1, int r2, int c2) {
        int dr = Math.abs(r1 - r2), dc = Math.abs(c1 - c2);
        return (dr + dc) == 1;
    }

    public boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    /** Kiem tra co the di chuyen tu (fr,fc) sang o ke (tr,tc) hay khong. */
    public boolean canMovePawn(int fr, int fc, int tr, int tc) {
        if (!inBounds(tr, tc)) return false;
        if (!isAdjacent(fr, fc, tr, tc)) return false;
        if (isEdgeBlocked(fr, fc, tr, tc)) return false;
        // khong duoc di vao o dang co quan doi phuong dung
        if ((tr == p1Row && tc == p1Col) || (tr == p2Row && tc == p2Col)) return false;
        return true;
    }

    public void movePawn(int player, int tr, int tc) {
        if (player == 1) { p1Row = tr; p1Col = tc; }
        else { p2Row = tr; p2Col = tc; }
    }

    public boolean hasReachedGoal(int player) {
        return player == 1 ? p1Row == 8 : p2Row == 0;
    }

    // ---------- BARRICADE ----------

    public boolean inWallBounds(int r, int c) {
        return r >= 0 && r < SIZE - 1 && c >= 0 && c < SIZE - 1;
    }

    /** Kiem tra vi tri wall hop le ve mat hinh hoc: trong pham vi, khong trung/khong giao voi wall khac. */
    public boolean isGeometryValid(int row, int col, String orientation) {
        if (!inWallBounds(row, col)) return false;
        for (Wall w : walls) {
            if (w.row == row && w.col == col) return false; // trung vi tri (kha ca giao nhau tai 1 diem)
            if (w.orientation.equals(orientation)) {
                if (orientation.equals("H") && w.row == row && Math.abs(w.col - col) == 1) return false; // chong lan
                if (orientation.equals("V") && w.col == col && Math.abs(w.row - row) == 1) return false; // chong lan
            }
        }
        return true;
    }

    /** Them wall tam thoi de kiem tra, tra ve true neu ca 2 nguoi choi van con duong den dich. */
    public boolean wouldBlockPath(int row, int col, String orientation) {
        walls.add(new Wall(row, col, orientation));
        boolean p1Ok = bfsCanReach(p1Row, p1Col, 8);
        boolean p2Ok = bfsCanReach(p2Row, p2Col, 0);
        walls.remove(new Wall(row, col, orientation));
        return !(p1Ok && p2Ok);
    }

    public boolean isValidWallPlacement(int player, int row, int col, String orientation) {
        int left = player == 1 ? p1BarricadesLeft : p2BarricadesLeft;
        if (left <= 0) return false;
        if (!"H".equals(orientation) && !"V".equals(orientation)) return false;
        if (!isGeometryValid(row, col, orientation)) return false;
        return !wouldBlockPath(row, col, orientation);
    }

    public void placeWall(int player, int row, int col, String orientation) {
        walls.add(new Wall(row, col, orientation));
        if (player == 1) p1BarricadesLeft--; else p2BarricadesLeft--;
    }

    public void clearWalls() {
        walls.clear();
    }

    /** BFS kiem tra tu (startRow,startCol) co the den duoc hang targetRow hay khong. */
    private boolean bfsCanReach(int startRow, int startCol, int targetRow) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[0] == targetRow) return true;
            for (int[] d : dirs) {
                int nr = cur[0] + d[0], nc = cur[1] + d[1];
                if (inBounds(nr, nc) && !visited[nr][nc] && !isEdgeBlocked(cur[0], cur[1], nr, nc)) {
                    visited[nr][nc] = true;
                    queue.add(new int[]{nr, nc});
                }
            }
        }
        return false;
    }

    /** Kiem tra canh giua 2 o ke nhau co bi wall nao chan khong (khong tinh quan co doi phuong). */
    private boolean isEdgeBlocked(int r1, int c1, int r2, int c2) {
        for (Wall w : walls) {
            if (w.orientation.equals("H")) {
                // H wall tai (w.row,w.col) chan canh doc giua (w.row,w.col)-(w.row+1,w.col)
                // va (w.row,w.col+1)-(w.row+1,w.col+1)
                if (blocksVerticalEdge(w, r1, c1, r2, c2)) return true;
            } else {
                if (blocksHorizontalEdge(w, r1, c1, r2, c2)) return true;
            }
        }
        return false;
    }

    private boolean blocksVerticalEdge(Wall w, int r1, int c1, int r2, int c2) {
        if (c1 != c2) return false; // canh doc thi cung cot
        int top = Math.min(r1, r2), bot = Math.max(r1, r2);
        if (bot - top != 1) return false;
        return (top == w.row) && (c1 == w.col || c1 == w.col + 1);
    }

    private boolean blocksHorizontalEdge(Wall w, int r1, int c1, int r2, int c2) {
        if (r1 != r2) return false; // canh ngang thi cung hang
        int left = Math.min(c1, c2), right = Math.max(c1, c2);
        if (right - left != 1) return false;
        return (left == w.col) && (r1 == w.row || r1 == w.row + 1);
    }

    public BoardState toBoardState() {
        BoardState s = new BoardState();
        s.size = SIZE;
        s.p1Row = p1Row; s.p1Col = p1Col;
        s.p2Row = p2Row; s.p2Col = p2Col;
        s.p1BarricadesLeft = p1BarricadesLeft;
        s.p2BarricadesLeft = p2BarricadesLeft;
        s.walls = new ArrayList<>(walls);
        return s;
    }
}
