package common.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Mot barricade (wall) chan 2 o lien tiep. Wall duoc dinh nghia boi
 * o goc (row, col) - la o phia tren-trai cua cap o 2x2 ma no chan -
 * va huong: "H" (ngang, chan di chuyen theo chieu doc) hoac
 * "V" (doc, chan di chuyen theo chieu ngang). Moi wall chiem 2 don vi
 * canh lien tiep, giong luat Quoridor.
 */
public class Wall implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int row;
    public final int col;
    public final String orientation; // "H" hoac "V"

    public Wall(int row, int col, String orientation) {
        this.row = row;
        this.col = col;
        this.orientation = orientation;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Wall)) return false;
        Wall w = (Wall) o;
        return row == w.row && col == w.col && orientation.equals(w.orientation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col, orientation);
    }

    @Override
    public String toString() {
        return "Wall(" + row + "," + col + "," + orientation + ")";
    }
}
