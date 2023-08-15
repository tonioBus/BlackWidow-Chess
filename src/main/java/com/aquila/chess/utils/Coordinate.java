package com.aquila.chess.utils;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
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
@Getter
public class Coordinate {
    private int xInput;
    private int yInput;
    private Alliance alliance;

    /**
     * Create a coordinate <strong>WITHOUT</strong> taking care of the alliance
     * @param xInput
     * @param yInput
     * @param alliance
     */
    public Coordinate(int xInput, int yInput, Alliance alliance) {
        this.xInput = xInput;
        this.yInput = yInput;
        this.alliance = alliance;
    }

    /**
     * Build a coordinate based on the destination of the move, <strong>NOT</strong> the origin
     * This Constructor take care of the alliance to get coordinate from the player point of view
     *
     * @param move
     */
    public static Coordinate destinationCoordinate(final Move move) {
        return new Coordinate(move.getDestinationCoordinate(), move.getAllegiance());
    }

    /**
     * Build a coordinate based on the source of the move, <strong>NOT</strong> the destination
     * This Constructor take care of the alliance to get coordinate from the player point of view
     *
     * @param move
     */
    public static Coordinate sourceCoordinate(final Move move) {
        return new Coordinate(move.getCurrentCoordinate(), move.getAllegiance());
    }

    /**
     * Convert from position as given by initializeAlgebraicNotation to cartesian coordinate
     * This Constructor take care of the alliance to get coordinate from the player point of view
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
