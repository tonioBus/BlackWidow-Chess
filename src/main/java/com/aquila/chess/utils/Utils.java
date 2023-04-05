/**
 *
 */
package com.aquila.chess.utils;

import com.aquila.chess.strategy.mcts.inputs.InputsNNFactory;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.pieces.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.shade.protobuf.common.io.PatternFilenameFilter;
import umontreal.ssj.randvarmulti.DirichletGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

/**
 * @author bussa
 */
@Slf4j
public class Utils {

    static private final RandomStream stream = new MRG32k3a();

    @Deprecated
    public static double getRandom(double min, double max, Random rand) {
        double zeroOne = rand.nextDouble();
        return (max - min) * zeroOne + min;
    }

    /**
     * Fisher Yates shuffle Algorithm
     *
     * @param arr
     * @param rand
     */
    @Deprecated
    public static void randomize(Object[] arr, Random rand) {
        int n = arr.length;

        // Start from the last element and swap one by one. We don't
        // need to run for the first element that's why i > 0
        for (int i = n - 1; i > 0; i--) {
            // Pick a random index from 0 to i
            int j = rand.nextInt(i);
            // Swap arr[i] with the element at random index
            Object temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
    }

    @Deprecated
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
    public static double[] toDistribution(double[] policies, int[] indexes, boolean isDirichlet) {
        double sum = 0;
        for (int i = 0; i < policies.length; i++) {
            if (ArrayUtils.contains(indexes, i)) {
                sum += policies[i];
            }
        }
        if (indexes.length > 0 && sum == 0) {
            throw new RuntimeException("toDistribution(): sum of policies==0 !!!");
        }
        for (int i = 0; i < policies.length; i++) {
            if (sum > 0 && ArrayUtils.contains(indexes, i)) {
                policies[i] = policies[i] / sum;
            } else {
                policies[i] = 0;
            }
        }
        if (isDirichlet) {
            log.info("Policy > 0: {}", Arrays.stream(policies).filter(policy -> policy > 0).count());
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
            log.warn("dirichlet: indexes: {}", indexes.length);
        }
        return policies;
    }

    public static int nbMaxBits(long number) {
        return (int) (Math.log(Long.highestOneBit(number)) / Math.log(2.0));
    }

    public static String toSha1(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return UUID.nameUUIDFromBytes(md.digest(text.getBytes())).toString();
    }

    static public int maxGame(String path) {
        File dataDirectory = new File(path); // "train/"
        int max = 0;
        if (dataDirectory.canRead()) {
            for (File file : dataDirectory.listFiles(new PatternFilenameFilter("[0-9]+"))) {
                int currentNumber = Integer.valueOf(file.getName()).intValue();
                if (currentNumber > max) max = currentNumber;
            }
        }
        return max;
    }

    /**
     * Display the board located in inputs at offset boardIndex (offset within the 8 encoded board)
     *
     * @param inputs
     * @param boardIndex
     * @return
     */
    static public String displayBoard(double[][][] inputs, int boardIndex) {
        Board.Builder builder = new Board.Builder();
        boolean empty = true;
        for (Alliance color : Arrays.asList(Alliance.WHITE, Alliance.BLACK)) {
            for (int piece = InputsNNFactory.PAWN_INDEX; piece <= InputsNNFactory.KING_INDEX; piece++) {
                int indexInput = (piece + 6 * color.ordinal()) + (boardIndex * 13);
                for (int x = 0; x < Board.NB_COL; x++) {
                    for (int y = 0; y < Board.NB_COL; y++) {
                        if (inputs[indexInput][x][y] != 0.0) {
                            int piecePosition;
                            if (color.isWhite()) {
                                piecePosition = 64 - ((8 - x) + y * 8);
                            } else {
                                piecePosition = ((7 - x) + y * 8);
                            }
                            switch (piece) {
                                case InputsNNFactory.PAWN_INDEX:
                                    log.trace("PAWN color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Pawn(color, piecePosition));
                                    break;
                                case InputsNNFactory.BISHOP_INDEX:
                                    log.trace("BISHOP color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Bishop(color, piecePosition));
                                    break;
                                case InputsNNFactory.KING_INDEX:
                                    log.trace("KING color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new King(color, piecePosition, false, false));
                                    break;
                                case InputsNNFactory.KNIGHT_INDEX:
                                    log.trace("KNIGHT color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Knight(color, piecePosition));
                                    break;
                                case InputsNNFactory.ROOK_INDEX:
                                    log.trace("ROOK color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Rook(color, piecePosition));
                                    break;
                                case InputsNNFactory.QUEEN_INDEX:
                                    log.trace("QUEEN color:{} position:{} x:{} y:{}", color, piecePosition, x, y);
                                    builder.setPiece(new Queen(color, piecePosition));
                                    break;
                            }
                            empty = false;
                        }
                    }
                }
            }
        }
        if (empty) return null;
        builder.setMoveMaker(Alliance.WHITE);
        return builder.build().toString();
    }

}

