package client.ui;

import common.model.BoardState;
import common.model.Wall;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class BoardCanvas extends Canvas {
    private static final int CELL = 46;
    private static final int GAP = 14;
    private static final int SIZE = 9;
    private static final int MARGIN = 10;

    private BoardState state = new BoardState();
    private BiConsumer<Integer, Integer> onCellClick;
    private BiConsumer<Integer, Integer> onWallClick;

    private String mode = "MOVE"; // MOVE | H | V
    private int hoverRow = -1, hoverCol = -1;

    private int mySymbol = 0;
    private boolean myTurn = false;
    private final List<int[]> validMoves = new ArrayList<>();

    private static final long ANIM_DURATION_NS = 220_000_000L;
    private static final long HIGHLIGHT_FADE_NS = 700_000_000L;

    private AnimationTimer animTimer;
    private long animStartNanos;
    private int animPlayer = -1;
    private double animFromX, animFromY, animToX, animToY, animCurX, animCurY;

    private int lastFromRow = -1, lastFromCol = -1, lastToRow = -1, lastToCol = -1;
    private AnimationTimer fadeTimer;
    private long fadeStartNanos;
    private double highlightAlpha = 0;

    public BoardCanvas() {
        int dim = SIZE * CELL + (SIZE - 1) * GAP + 2 * MARGIN;
        setWidth(dim);
        setHeight(dim);

        setOnMouseClicked(this::handleClick);
        setOnMouseMoved(this::handleMouseMoved);
        setOnMouseExited(e -> { if (hoverRow != -1) { hoverRow = -1; hoverCol = -1; draw(); } });

        draw();
    }

    private void handleClick(MouseEvent e) {
        int x = (int) (e.getX() - MARGIN), y = (int) (e.getY() - MARGIN);
        if ("MOVE".equals(mode)) {
            int[] cell = pixelToCell(x, y);
            if (cell != null && onCellClick != null) onCellClick.accept(cell[0], cell[1]);
        } else {
            int[] slot = nearestWallSlot(x, y, mode);
            if (slot != null && onWallClick != null) onWallClick.accept(slot[0], slot[1]);
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        if ("MOVE".equals(mode)) {
            if (hoverRow != -1) { hoverRow = -1; hoverCol = -1; draw(); }
            return;
        }
        int x = (int) (e.getX() - MARGIN), y = (int) (e.getY() - MARGIN);
        int[] slot = nearestWallSlot(x, y, mode);
        if (slot == null) return;
        if (slot[0] != hoverRow || slot[1] != hoverCol) {
            hoverRow = slot[0]; hoverCol = slot[1];
            draw();
        }
    }

    public void setMode(String mode) {
        this.mode = mode;
        hoverRow = -1; hoverCol = -1;
        recomputeValidMoves();
        draw();
    }

    public void setOnCellClick(BiConsumer<Integer, Integer> callback) { this.onCellClick = callback; }
    public void setOnWallClick(BiConsumer<Integer, Integer> callback) { this.onWallClick = callback; }

    public void setMySymbol(int symbol) {
        this.mySymbol = symbol;
        recomputeValidMoves();
        draw();
    }

    public void setMyTurn(boolean myTurn) {
        this.myTurn = myTurn;
        recomputeValidMoves();
        draw();
    }

    public void updateState(BoardState newState) {
        BoardState oldState = this.state;
        this.state = newState;
        recomputeValidMoves();

        int movedPlayer = detectMovedPlayer(oldState, newState);
        if (movedPlayer != -1) {
            int fromRow = movedPlayer == 1 ? oldState.p1Row : oldState.p2Row;
            int fromCol = movedPlayer == 1 ? oldState.p1Col : oldState.p2Col;
            int toRow = movedPlayer == 1 ? newState.p1Row : newState.p2Row;
            int toCol = movedPlayer == 1 ? newState.p1Col : newState.p2Col;
            startMoveAnimation(movedPlayer, fromRow, fromCol, toRow, toCol);
        } else {
            draw();
        }
    }

    private int detectMovedPlayer(BoardState oldState, BoardState newState) {
        if (oldState == null) return -1;
        boolean p1Moved = oldState.p1Row != newState.p1Row || oldState.p1Col != newState.p1Col;
        boolean p2Moved = oldState.p2Row != newState.p2Row || oldState.p2Col != newState.p2Col;
        if (p1Moved && !p2Moved) return 1;
        if (p2Moved && !p1Moved) return 2;
        return -1;
    }

    private void startMoveAnimation(int player, int fromRow, int fromCol, int toRow, int toCol) {
        if (animTimer != null) animTimer.stop();
        if (fadeTimer != null) fadeTimer.stop();

        animPlayer = player;
        animFromX = fromCol * (CELL + GAP);
        animFromY = fromRow * (CELL + GAP);
        animToX = toCol * (CELL + GAP);
        animToY = toRow * (CELL + GAP);
        animCurX = animFromX;
        animCurY = animFromY;
        animStartNanos = System.nanoTime();
        highlightAlpha = 0;

        lastFromRow = fromRow; lastFromCol = fromCol;
        lastToRow = toRow; lastToCol = toCol;

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long elapsed = now - animStartNanos;
                double t = Math.min(1.0, elapsed / (double) ANIM_DURATION_NS);
                double eased = easeOutCubic(t);
                animCurX = animFromX + (animToX - animFromX) * eased;
                animCurY = animFromY + (animToY - animFromY) * eased;
                draw();
                if (t >= 1.0) {
                    stop();
                    animPlayer = -1;
                    startHighlightFade();
                }
            }
        };
        animTimer.start();
    }

    private double easeOutCubic(double t) {
        double f = t - 1;
        return f * f * f + 1;
    }

    private void startHighlightFade() {
        highlightAlpha = 1.0;
        fadeStartNanos = System.nanoTime();
        fadeTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long elapsed = now - fadeStartNanos;
                highlightAlpha = Math.max(0, 1.0 - elapsed / (double) HIGHLIGHT_FADE_NS);
                draw();
                if (elapsed >= HIGHLIGHT_FADE_NS) {
                    stop();
                    highlightAlpha = 0;
                    lastFromRow = -1; lastFromCol = -1;
                    lastToRow = -1; lastToCol = -1;
                    draw();
                }
            }
        };
        fadeTimer.start();
    }

    // ---------- HIGHLIGHT NUOC DI HOP LE ----------

    private void recomputeValidMoves() {
        validMoves.clear();
        if (!"MOVE".equals(mode) || !myTurn || mySymbol == 0 || state == null) return;

        int fr = mySymbol == 1 ? state.p1Row : state.p2Row;
        int fc = mySymbol == 1 ? state.p1Col : state.p2Col;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int tr = fr + d[0], tc = fc + d[1];
            if (tr < 0 || tr >= SIZE || tc < 0 || tc >= SIZE) continue;
            if (isEdgeBlockedByWalls(fr, fc, tr, tc)) continue;
            if ((tr == state.p1Row && tc == state.p1Col) || (tr == state.p2Row && tc == state.p2Col)) continue;
            validMoves.add(new int[]{tr, tc});
        }
    }

    private boolean isEdgeBlockedByWalls(int r1, int c1, int r2, int c2) {
        for (Wall w : state.walls) {
            if ("H".equals(w.orientation)) {
                if (blocksVerticalEdge(w, r1, c1, r2, c2)) return true;
            } else {
                if (blocksHorizontalEdge(w, r1, c1, r2, c2)) return true;
            }
        }
        return false;
    }

    private boolean blocksVerticalEdge(Wall w, int r1, int c1, int r2, int c2) {
        if (c1 != c2) return false;
        int top = Math.min(r1, r2), bot = Math.max(r1, r2);
        if (bot - top != 1) return false;
        return (top == w.row) && (c1 == w.col || c1 == w.col + 1);
    }

    private boolean blocksHorizontalEdge(Wall w, int r1, int c1, int r2, int c2) {
        if (r1 != r2) return false;
        int left = Math.min(c1, c2), right = Math.max(c1, c2);
        if (right - left != 1) return false;
        return (left == w.col) && (r1 == w.row || r1 == w.row + 1);
    }

    private int[] pixelToCell(int x, int y) {
        int col = x / (CELL + GAP);
        int row = y / (CELL + GAP);
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return null;
        int cellX = x - col * (CELL + GAP);
        int cellY = y - row * (CELL + GAP);
        if (cellX > CELL || cellY > CELL) return null;
        return new int[]{row, col};
    }

    private int[] nearestWallSlot(int x, int y, String orientation) {
        int bestRow = -1, bestCol = -1;
        double bestDist = Double.MAX_VALUE;
        for (int r = 0; r < SIZE - 1; r++) {
            for (int c = 0; c < SIZE - 1; c++) {
                int cx, cy, w, h;
                if ("H".equals(orientation)) {
                    cx = c * (CELL + GAP);
                    cy = r * (CELL + GAP) + CELL;
                    w = 2 * CELL + GAP;
                    h = GAP;
                } else {
                    cx = c * (CELL + GAP) + CELL;
                    cy = r * (CELL + GAP);
                    w = GAP;
                    h = 2 * CELL + GAP;
                }
                double centerX = cx + w / 2.0, centerY = cy + h / 2.0;
                double dist = Math.hypot(x - centerX, y - centerY);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }
        if (bestRow == -1) return null;
        return new int[]{bestRow, bestCol};
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        g.setFill(Color.rgb(235, 222, 199));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.save();
        g.translate(MARGIN, MARGIN);

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int x = c * (CELL + GAP), y = r * (CELL + GAP);
                g.setFill(Color.rgb(250, 244, 230));
                g.fillRect(x, y, CELL, CELL);
                g.setStroke(Color.rgb(150, 130, 100));
                g.strokeRect(x, y, CELL, CELL);
            }
        }

        if (lastFromRow >= 0) {
            double alpha = 90 * Math.max(highlightAlpha, 0.35) / 255.0;
            drawCellHighlight(g, lastFromRow, lastFromCol, Color.rgb(255, 210, 80, alpha));
        }
        if (lastToRow >= 0) {
            double alpha = 200 * Math.max(highlightAlpha, 0.25) / 255.0;
            drawCellHighlight(g, lastToRow, lastToCol, Color.rgb(255, 235, 59, alpha));
        }

        for (int[] mv : validMoves) {
            drawValidMoveMarker(g, mv[0], mv[1]);
        }

        if (!"MOVE".equals(mode) && hoverRow >= 0) {
            g.setFill(Color.rgb(120, 60, 20, 110 / 255.0));
            drawWallShape(g, hoverRow, hoverCol, mode);
        }

        g.setFill(Color.rgb(120, 60, 20));
        for (Wall w : state.walls) {
            drawWallShape(g, w.row, w.col, w.orientation);
        }

        if (animPlayer == 1) {
            drawPawnAtPixel(g, animCurX, animCurY, Color.rgb(30, 90, 200));
            drawPawn(g, state.p2Row, state.p2Col, Color.rgb(200, 40, 40));
        } else if (animPlayer == 2) {
            drawPawn(g, state.p1Row, state.p1Col, Color.rgb(30, 90, 200));
            drawPawnAtPixel(g, animCurX, animCurY, Color.rgb(200, 40, 40));
        } else {
            drawPawn(g, state.p1Row, state.p1Col, Color.rgb(30, 90, 200));
            drawPawn(g, state.p2Row, state.p2Col, Color.rgb(200, 40, 40));
        }

        g.restore();
    }

    private void drawCellHighlight(GraphicsContext g, int row, int col, Color color) {
        int x = col * (CELL + GAP), y = row * (CELL + GAP);
        g.setFill(color);
        g.fillRoundRect(x - 2, y - 2, CELL + 4, CELL + 4, 10, 10);
    }

    private void drawValidMoveMarker(GraphicsContext g, int row, int col) {
        int x = col * (CELL + GAP), y = row * (CELL + GAP);
        int pad = 13;
        g.setFill(Color.rgb(76, 217, 100, 140 / 255.0));
        g.fillOval(x + pad, y + pad, CELL - 2 * pad, CELL - 2 * pad);
        g.setStroke(Color.rgb(34, 139, 58, 200 / 255.0));
        g.setLineWidth(2);
        g.strokeOval(x + pad, y + pad, CELL - 2 * pad, CELL - 2 * pad);
    }

    private void drawWallShape(GraphicsContext g, int row, int col, String orientation) {
        if ("H".equals(orientation)) {
            int x = col * (CELL + GAP);
            int y = row * (CELL + GAP) + CELL;
            g.fillRoundRect(x, y, 2 * CELL + GAP, GAP, 6, 6);
        } else {
            int x = col * (CELL + GAP) + CELL;
            int y = row * (CELL + GAP);
            g.fillRoundRect(x, y, GAP, 2 * CELL + GAP, 6, 6);
        }
    }

    private void drawPawn(GraphicsContext g, int row, int col, Color color) {
        drawPawnAtPixel(g, col * (CELL + GAP), row * (CELL + GAP), color);
    }

    private void drawPawnAtPixel(GraphicsContext g, double x, double y, Color color) {
        int pad = 8;
        g.setFill(color);
        g.fillOval(x + pad, y + pad, CELL - 2 * pad, CELL - 2 * pad);
        g.setStroke(Color.BLACK);
        g.strokeOval(x + pad, y + pad, CELL - 2 * pad, CELL - 2 * pad);
    }
}