package com.aquila.chess.utils;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * cartesian coordinates when looking to the board as white player
 * <ul>
 *     <li>a8 -> [0,7]</li>
 *     <li>h8 -> [7,7]</li>
 *     <li>a1 -> [0,0]</li>
 *     <li>e2 -> [4,1]</li>
 * </ul>
 */
@AllArgsConstructor
@Getter
public class Coordinate {
    private int xInput;
    private int yInput;
    private Alliance alliance;

    /**
     * Convert from position as given by initializeAlgebraicNotation to cartesian coordinate
     * <ul>
     *     <li>a8 -> <strong>0</strong> -> [0,7]</li>
     *     <li>h8 -> <strong>7</strong> -> [7,7]</li>
     *     <li>a1 -> <strong>56</strong> -> [0,0]</li>
     *     <li>e2 -> <strong>52</strong> -> [4,1]</li>
     * </ul>
     *
     * @param position
     * @return
     */
    public Coordinate(int position, Alliance alliance) {
        this.alliance = alliance;
        this.xInput = position % 8;
        this.yInput = 7 - (position / 8); // we should NOT round up and keep down-casting
        if (this.alliance == Alliance.BLACK) {
            this.xInput = 7 - this.xInput;
            this.yInput = 7 - this.yInput;
        }
    }

    public Coordinate(String positionSz, Alliance alliance) {
        this(BoardUtils.ALGEBRAIC_NOTATION.indexOf(positionSz), alliance);
    }

    /**
     * build the coordinate using the given piece, the color of the piece will change the coordinate to be always
     * on the player point of view (black pieces are reversed)
     *
     * @param piece
     */
    public Coordinate(final Piece piece) {
        this(piece.getPiecePosition(), piece.getPieceAllegiance());
    }

    /**
     * Build a coordinate based on the destination of the move, <strong>NOT</strong> the origin
     *
     * @param move
     */
    public Coordinate(final Move move) {
        this(move.getDestinationCoordinate(), move.getAllegiance());
    }

    public int getBoardPosition() {
        int x = this.xInput;
        int y = this.yInput;
        if (this.alliance == Alliance.BLACK) {
            x = 7 - x;
            y = 7 - y;
        }
        return (7 - y) * 8 + x;
    }

    public String toString() {
        return BoardUtils.ALGEBRAIC_NOTATION.get(getBoardPosition());
    }
}
