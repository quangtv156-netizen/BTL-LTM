package common.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Snapshot trang thai ban co, gui tu Server sang Client de ve lai giao dien. */
public class BoardState implements Serializable {
    private static final long serialVersionUID = 1L;

    public int size = 9;
    public int p1Row, p1Col;
    public int p2Row, p2Col;
    public int p1BarricadesLeft;
    public int p2BarricadesLeft;
    public List<Wall> walls = new ArrayList<>();

    public BoardState copy() {
        BoardState b = new BoardState();
        b.size = size;
        b.p1Row = p1Row; b.p1Col = p1Col;
        b.p2Row = p2Row; b.p2Col = p2Col;
        b.p1BarricadesLeft = p1BarricadesLeft;
        b.p2BarricadesLeft = p2BarricadesLeft;
        b.walls = new ArrayList<>(walls);
        return b;
    }
}
