package com.aquila.chess.strategy.mcts;

import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

import static com.chess.engine.classic.board.BoardUtils.NUM_TILES_PER_ROW;

@Slf4j
public class PolicyUtils {

    static public final int MAX_POLICY_INDEX = 4672;

    public static int indexFromMove(final Move move, final Piece piece) {
        if (move == null || piece == null) return 0;
        int startX = move.getCurrentCoordinate();
        int startY = move.getCurrentCoordinate();
        int endX = move.getDestinationCoordinate();
        int endY = move.getDestinationCoordinate();
        return indexFromMove(startX, startY, endX, endY, piece);
    }

    public static int indexFromMove(final Move move) {
        Coordinate2D srcCoordinate2D = new Coordinate2D(move.getCurrentCoordinate());
        Coordinate2D destCoordinate2D = new Coordinate2D(move.getDestinationCoordinate());
        return indexFromMove(
                srcCoordinate2D.getX(),
                srcCoordinate2D.getY(),
                destCoordinate2D.getX(),
                destCoordinate2D.getY(),
                move.getMovedPiece());
    }

    /**
     * @return
     * @formatter:off <pre>
     *   out: 8x8x73: [0..72]  ->     [0..55)(Queen moves: nbStep + orientation) [56..63](Knights moves) [64..72](underpromotion)
     * Queen moves: [1 .. 7] ->     7 number of steps  [N,NE,E,SE,S,SW,W,NW]: 8 orientation -> 7*8
     * Knight moves: [0..7]  ->     [Up+Up+Left,Up+Up+Right,Right+Right+Up, Right+Right+Down,
     * Down+Down+Right, Down+Down+Left,Left+Left+Down,Left+Left+Up]
     * UnderPromotion:
     * </pre>
     * @formatter:on
     */
    public static int indexFromMove(int startX, int startY, int endX, int endY, final Piece piece) {
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
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 0);
                    else ret = indexFromKnightMove(startX, startY, 1);
                    break;
                case 300: // SE
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 2);
                    else ret = indexFromKnightMove(startX, startY, 3);
                    break;
                case 100: // SW
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 4);
                    else ret = indexFromKnightMove(startX, startY, 5);
                    break;
                case 102: // NW
                    if (nbStepY > nbStepX) ret = indexFromKnightMove(startX, startY, 6);
                    else ret = indexFromKnightMove(startX, startY, 7);
                    break;
            }
        } else {
            switch (deltaX + deltaY) {
                case 202:
                    ret = indexFromQueenMove(startX, startY, nbStep, 0);
                    break;
                case 302:
                    ret = indexFromQueenMove(startX, startY, nbStep, 1);
                    break;
                case 301:
                    ret = indexFromQueenMove(startX, startY, nbStep, 2);
                    break;
                case 300:
                    ret = indexFromQueenMove(startX, startY, nbStep, 3);
                    break;
                case 200:
                    ret = indexFromQueenMove(startX, startY, nbStep, 4);
                    break;
                case 100:
                    ret = indexFromQueenMove(startX, startY, nbStep, 5);
                    break;
                case 101:
                    ret = indexFromQueenMove(startX, startY, nbStep, 6);
                    break;
                case 102:
                    ret = indexFromQueenMove(startX, startY, nbStep, 7);
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
     * @param x           [0 .. 7] coordinate x of the piece to move
     * @param y           [0 .. 7] coordinate Y of the piece to move
     * @param nbStep      [1..7] number of step (absolute)
     * @param orientation orientation of the move starting with index 0 ->
     *                    [N,NE,E,SE,S,SW,W,NW]
     * @return
     */
    private static int indexFromQueenMove(int x, int y, int nbStep, int orientation) {
        return to1D(x, NUM_TILES_PER_ROW, y, NUM_TILES_PER_ROW, nbStep + orientation * NUM_TILES_PER_ROW);
    }

    /**
     * @param x          [0..7] coordinate x of the knight to move
     * @param y          [0..7] coordinate Y of the knight to move
     * @param knightMove [0..7] [Up+Up+Left,Up+Up+Right,Right+Right+Up,
     *                   Right+Right+Down, Down+Down+Right,
     *                   Down+Down+Left,Left+Left+Down,Left+Left+Up]
     * @return
     */
    private static int indexFromKnightMove(int x, int y, int knightMove) {
        return to1D(x, NUM_TILES_PER_ROW, y, NUM_TILES_PER_ROW, 56 + knightMove);
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
    public static int[] getIndexesFilteredPolicies(Collection<Move> moves) {
        return moves.stream().filter((move) -> move != null).mapToInt((move) -> PolicyUtils.indexFromMove(move)).toArray();
    }

}
