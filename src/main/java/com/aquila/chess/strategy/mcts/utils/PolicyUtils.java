package com.aquila.chess.strategy.mcts.utils;

import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import umontreal.ssj.randvarmulti.DirichletGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.chess.engine.classic.board.BoardUtils.NUM_TILES_PER_ROW;

@Slf4j
public class PolicyUtils {

    static public final int MAX_POLICY_INDEX = 4672;

    static private final RandomStream stream;

    /**
     * return policy index from a move
     *
     * @param move
     * @return
     */
    public static int indexFromMove(final Move move) {
        Coordinate2D srcCoordinate2D = new Coordinate2D(move.getCurrentCoordinate());
        Coordinate2D destCoordinate2D = new Coordinate2D(move.getDestinationCoordinate());
        return indexFromMove(
                srcCoordinate2D.getX(),
                srcCoordinate2D.getY(),
                destCoordinate2D.getX(),
                destCoordinate2D.getY(),
                move.getMovedPiece().getPieceType()
        );
    }

    /**
     * return policy index from a move
     *
     * @param start the start in algrebic notation
     * @param end   the end in algebric notation
     * @return the index of the given move
     */
    public static int indexFromMove(final Piece.PieceType pieceType, String start, String end) {
        Coordinate2D srcCoordinate2D = new Coordinate2D(BoardUtils.INSTANCE.getCoordinateAtPosition(start));
        Coordinate2D destCoordinate2D = new Coordinate2D(BoardUtils.INSTANCE.getCoordinateAtPosition(end));
        return indexFromMove(
                srcCoordinate2D.getX(),
                srcCoordinate2D.getY(),
                destCoordinate2D.getX(),
                destCoordinate2D.getY(),
                pieceType
        );
    }

    public static String moveFromIndex(int index, Collection<Move> moves) {
        List<Move> filteredMoves = moves.stream().filter(move -> index == indexFromMove(move)).collect(Collectors.toList());
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
    public static int indexFromMove(int startX, int startY, int endX, int endY, final Piece.PieceType pieceType) {
        int ret = 0;
        int deltaX = endX - startX;
        int nbStepX = Math.abs(deltaX);
        deltaX = deltaX < 0 ? 0 : deltaX > 0 ? 2 : 1;
        int deltaY = endY - startY;
        int nbStepY = Math.abs(deltaY);
        int nbStep = Math.max(nbStepX, nbStepY);
        deltaY = deltaY < 0 ? 100 : deltaY > 0 ? 300 : 200;
        if (pieceType == Piece.PieceType.KNIGHT) {
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
     * MAX: 4095
     *
     * @param x           [0 .. 7] coordinate x of the piece to move
     * @param y           [0 .. 7] coordinate Y of the piece to move
     * @param nbStep      [1..7] number of step (absolute)
     * @param orientation orientation of the move starting with index 0 ->
     *                    [N,NE,E,SE,S,SW,W,NW]
     * @return
     */
    private static int indexFromQueenMove(int x, int y, int nbStep, int orientation) {
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
    private static int indexFromKnightMove(int x, int y, int knightMove) {
        int offset = NUM_TILES_PER_ROW - 1
                + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW
                + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW
                + (NUM_TILES_PER_ROW - 1) * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
        return offset
                + x
                + y * NUM_TILES_PER_ROW
                + knightMove * NUM_TILES_PER_ROW * NUM_TILES_PER_ROW;
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

    public static double[] normalise(double[] policies) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double policy : policies) {
            if (policy < min)
                min = policy;
            if (policy > max)
                max = policy;
        }
        double maxMin = max - min;
        if (maxMin > 0) {
            for (int i = 0; i < policies.length; i++) {
                policies[i] = (policies[i] - min) / maxMin;
            }
        }
        return policies;
    }

    /**
     * @param policies
     * @param indexes
     * @param isDirichlet
     * @return
     */
    public static double[] toDistribution(final double[] policies, int[] indexes, boolean isDirichlet, Collection<Move> moves) {
        double sum = 0;
        for (int i = 0; i < policies.length; i++) {
            if (ArrayUtils.contains(indexes, i)) {
                sum += policies[i];
            }
        }
        if (indexes.length > 0 && sum == 0) {
            log.warn("toDistribution(): sum of policies(nb:{})==0", policies.length);
            return policies;
        }
        for (int i = 0; i < policies.length; i++) {
            if (sum > 0 && ArrayUtils.contains(indexes, i)) {
                policies[i] = policies[i] / sum;
            } else {
                policies[i] = 0;
            }
        }
        if (isDirichlet) {
            if (log.isWarnEnabled()) {
                logPolicies("ORIGINAL ", policies, indexes, moves);
            }
            double[] alpha = new double[indexes.length];
            Arrays.fill(alpha, 0.3);
            DirichletGen dirichletGen = new DirichletGen(stream, alpha);
            double epsilon = 0.25;
            int index = 0;
            double[] d = new double[alpha.length];
            dirichletGen.nextPoint(d);
            double p;
            for (int i = 0; i < policies.length; i++) {
                if (ArrayUtils.contains(indexes, i)) {
                    p = policies[i];
                    double newP = (1 - epsilon) * p + epsilon * d[index];
                    policies[i] = (float) newP;
                    index++;
                }
            }
            if (log.isWarnEnabled()) {
                logPolicies("DIRICHLET", policies, indexes, moves);
            }
        }
        return policies;
    }

    public static void logPolicies(String label, final double[] policies, int[] indexes, Collection<Move> moves) {
        double maxPolicy = 0.0;
        double minPolicy = 1.0;
        int maxPolicyIndex = -1;
        int minPolicyIndex = -1;
        for (int i = 0; i < policies.length; i++) {
            if (policies[i] > maxPolicy) {
                maxPolicy = policies[i];
                maxPolicyIndex = i;
            }
            if (policies[i] > 0.0 && policies[i] < minPolicy) {
                minPolicy = policies[i];
                minPolicyIndex = i;
            }
        }
        log.warn("{}: MAX policy: {} index:{} move:{} | MIN policy: {} index:{} move:{}", label,
                maxPolicy, maxPolicyIndex, moveFromIndex(maxPolicyIndex, moves), minPolicy, minPolicyIndex, moveFromIndex(minPolicyIndex, moves));
        log.warn("{}: indexes: {} <-> {} : policies>0", label,
                indexes.length,
                Arrays.stream(policies).filter(policy -> policy > 0).count());
    }

    static {
        Random rand = new Random();
        long seed = System.currentTimeMillis();
        rand.setSeed(seed);
        log.info("Dirichlet SEED:{}", seed);
        long[] seeds = new long[]{
                rand.nextLong(4294967087L),
                rand.nextLong(4294967087L),
                rand.nextLong(4294967087L),
                rand.nextLong(4294944443L),
                rand.nextLong(4294944443l),
                rand.nextLong(4294944443l)};
        MRG32k3a.setPackageSeed(seeds);
        stream = new MRG32k3a();
    }
}
