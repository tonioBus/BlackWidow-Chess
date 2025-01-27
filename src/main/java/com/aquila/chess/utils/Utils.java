/**
 *
 */
package com.aquila.chess.utils;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.pieces.Piece;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.RuntimeMXBean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author bussa
 */
@Slf4j
public class Utils {

    @Getter
    private static boolean isDebuggerPresent = false;

    /**
     * <h3>Code from StackOverflow: Can a Java application detect that a debugger is attached?</h3>
     * @see <a href="https://stackoverflow.com/questions/5393403/can-a-java-application-detect-that-a-debugger-is-attached">stackoverflow.com</a>
     * @return true if a debugger is attached to the current JVM
     */
    static {
        // Get ahold of the Java Runtime Environment (JRE) management interface
        RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
        // Get the command line arguments that we were originally passed in
        List<String> args = runtime.getInputArguments();
        // Check if the Java Debug Wire Protocol (JDWP) agent is used.
        // One of the items might contain something like "-agentlib:jdwp=transport=dt_socket,address=9009,server=y,suspend=n"
        // We're looking for the string "jdwp".
        isDebuggerPresent = args.toString().contains("jdwp");
    }

    public static long hash(String str) {
        long hash = 5381;
        byte[] data = str.getBytes();
        for (byte b : data) {
            hash = ((hash << 5) + hash) + b;
        }
        return hash;
    }

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

    public static int nbMaxBits(long number) {
        return (int) (Math.log(Long.highestOneBit(number)) / Math.log(2.0));
    }

    public static String toSha1(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return UUID.nameUUIDFromBytes(md.digest(text.getBytes())).toString();
    }

    public static int hashCode1Alliance(final Board board, final Alliance alliance) {
        StringBuffer sb = new StringBuffer();
        for (int position = 0; position < BoardUtils.NUM_TILES; position++) {
            Piece piece = board.getPiece(position);
            if (piece != null && piece.getPieceAllegiance() == alliance) {
                sb.append(String.format("%s=%d", piece.getPieceType(), position));
            }
        }
        log.debug("hashCode1Alliance alliance:{}\n{}", alliance, sb);
        return sb.toString().hashCode();
    }

    public static double[] convertFloatsToDoubles(double[] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    private static final Pattern pattern = Pattern.compile("-?\\d+");

    public static boolean isInteger(String strNum) {
        if (strNum == null) {
            return false;
        }
        return pattern.matcher(strNum).matches();
    }
}

