package com.aquila.chess.strategy.mcts.utils;

import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.chess.engine.classic.board.BoardUtils.NUM_TILES_PER_ROW;

@Slf4j
public class PolicyUtils {

    static public final int MAX_POLICY_INDEX = 4672;

    /**
     * return policy index from a move
     *
     * @param move
     * @return
     */
    public static int indexFromMove(final Move move, boolean old) {
        Coordinate2D srcCoordinate2D = new Coordinate2D(move.getCurrentCoordinate());
        Coordinate2D destCoordinate2D = new Coordinate2D(move.getDestinationCoordinate());
        return indexFromMove(
                srcCoordinate2D.getX(),
                srcCoordinate2D.getY(),
                destCoordinate2D.getX(),
                destCoordinate2D.getY(),
                move.getMovedPiece()
                , old);
    }

    /**
     * return policy index from a move
     *
     * @param start the start in algrebic notation
     * @param end   the end in algebric notation
     * @return the index of the given move
     */
    public static int indexFromMove(final Piece piece, String start, String end, boolean old) {
        Coordinate2D srcCoordinate2D = new Coordinate2D(BoardUtils.INSTANCE.getCoordinateAtPosition(start));
        Coordinate2D destCoordinate2D = new Coordinate2D(BoardUtils.INSTANCE.getCoordinateAtPosition(end));
        if (piece == null) {
            throw new RuntimeException(String.format("Piece not found in %s", start));
        }
        return indexFromMove(
                srcCoordinate2D.getX(),
                srcCoordinate2D.getY(),
                destCoordinate2D.getX(),
                destCoordinate2D.getY(),
                piece,
                old);
    }

    public static String moveFromIndex(int index, Collection<Move> moves, boolean old) {
        List<Move> filteredMoves = moves.stream().filter(move -> index == indexFromMove(move, old)).collect(Collectors.toList());
        if (filteredMoves.isEmpty()) {
            // log.error("Index : {} not found on possible moves", index);
            return moves.stream().findAny().get().toString();
        }
        if (filteredMoves.size() != 1) {
            // log.error("Index : {} get multiple moves: {}", index, filteredMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
            return filteredMoves.get(0).toString();
        }
        return filteredMoves.get(0).toString();
    }

    /**
     * @return <pre>
     * out: 8x8x73: [0..72]  ->     [0..55](Queen moves: nbStep + orientation) [56..63](Knights moves) [64..72](underpromotion)
     * Queen moves: [1 .. 7] ->     7 number of steps  [N,NE,E,SE,S,SW,W,NW]: 8 orientation -> 7*8
     * Knight moves: [0..7]  ->     [Up+Up+Left,Up+Up+Right,Right+Right+Up, Right+Right+Down,
     * Down+Down+Right, Down+Down+Left,Left+Left+Down,Left+Left+Up]
     * UnderPromotion:
     * </pre>
     */
    public static int indexFromMove(int startX, int startY, int endX, int endY, final Piece piece, boolean old) {
        int ret = 0;
        int deltaX = endX - startX;
        int nbStepX = Math.abs(deltaX);
        deltaX = deltaX < 0 ? 0 : deltaX > 0 ? 2 : 1;
        int deltaY = endY - startY;
        int nbStepY = Math.abs(deltaY);
        int nbStep = Math.max(nbStepX, nbStepY);
        deltaY = deltaY < 0 ? 100 : deltaY > 0 ? 300 : 200;
        if (piece.getPieceType() == Piece.PieceType.KNIGHT) {
            switch (deltaX + deltaY) {
                case 302: // NE
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 0, old);
                    else ret = indexFromKnightMove(startX, startY, 1, old);
                    break;
                case 300: // SE
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 2, old);
                    else ret = indexFromKnightMove(startX, startY, 3, old);
                    break;
                case 100: // SW
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 4, old);
                    else ret = indexFromKnightMove(startX, startY, 5, old);
                    break;
                case 102: // NW
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 6, old);
                    else ret = indexFromKnightMove(startX, startY, 7, old);
                    break;
            }
        } else {
            switch (deltaX + deltaY) {
                case 202:
                    ret = indexFromQueenMove(startX, startY, nbStep, 0, old);
                    break;
                case 302:
                    ret = indexFromQueenMove(startX, startY, nbStep, 1, old);
                    break;
                case 301:
                    ret = indexFromQueenMove(startX, startY, nbStep, 2, old);
                    break;
                case 300:
                    ret = indexFromQueenMove(startX, startY, nbStep, 3, old);
                    break;
                case 200:
                    ret = indexFromQueenMove(startX, startY, nbStep, 4, old);
                    break;
                case 100:
                    ret = indexFromQueenMove(startX, startY, nbStep, 5, old);
                    break;
                case 101:
                    ret = indexFromQueenMove(startX, startY, nbStep, 6, old);
                    break;
                case 102:
                    ret = indexFromQueenMove(startX, startY, nbStep, 7, old);
                    break;
            }
        }
        if (ret > MAX_POLICY_INDEX) {
            log.info("(startX:{} startY:{}) -> (endX:{} endY:{})", startX, startY, endX, endY);
            log.info("indexFromMove nbStep: {} ret:{}", nbStep, ret);
        }
        return ret;
    }

    /**
     * MAX: 4095
     *
     * @param x           [0 .. 7] coordinate x of the piece to move
     * @param y           [0 .. 7] coordinate Y of the piece to move
     * @param nbStep      [1..7] number of step (absolute)
     * @param orientation orientation of the move starting with index 0 ->
     *                    [N,NE,E,SE,S,SW,W,NW]
     * @return
     */
    private static int indexFromQueenMove(int x, int y, int nbStep, int orientation, boolean old) {
        if (old)
            return to1D(x, NUM_TILES_PER_ROW, y, NUM_TILES_PER_ROW, nbStep + orientation * NUM_TILES_PER_ROW);
        else
            return x
                    + y * NUM_TILES_PER_ROW
                    + orientation * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW
                    + nbStep * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
        //nbStep * orientation + x * NUM_TILES_PER_ROW + y * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
    }

    /**
     * @param x          [0..7] coordinate x of the knight to move
     * @param y          [0..7] coordinate Y of the knight to move
     * @param knightMove [0..7] [Up+Up+Left,Up+Up+Right,Right+Right+Up,
     *                   Right+Right+Down, Down+Down+Right,
     *                   Down+Down+Left,Left+Left+Down,Left+Left+Up]
     * @return
     */
    private static int indexFromKnightMove(int x, int y, int knightMove, boolean old) {
        if (old)
            return to1D(x, NUM_TILES_PER_ROW, y, NUM_TILES_PER_ROW, 56 + knightMove);
        else {
            int offset = NUM_TILES_PER_ROW - 1
                    + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW
                    + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW
                    + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
            return offset
                    + x
                    + y * NUM_TILES_PER_ROW
                    + knightMove * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
        }
    }

    private static int to1D(int x, int xMax, int y, int yMax, int z) {
        return (x * xMax * yMax) + (y * xMax) + z;
    }

    /**
     * Convert moves into their corresponding array of index
     *
     * @param moves the list of move
     * @return - list of indexes using policies coding ([1 - 45XX])
     */
    public static int[] getIndexesFilteredPolicies(Collection<Move> moves, boolean old) {
        return moves.stream().filter((move) -> move != null).mapToInt((move) -> PolicyUtils.indexFromMove(move, old)).toArray();
    }

}
