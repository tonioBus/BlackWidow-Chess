/**
 *
 */
package com.aquila.chess.utils;

import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.shade.protobuf.common.io.PatternFilenameFilter;
import umontreal.ssj.randvarmulti.DirichletGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

import java.io.File;
import java.lang.management.RuntimeMXBean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * @author bussa
 */
@Slf4j
public class Utils {

    static private final RandomStream stream;

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
    public static double[] toDistribution(final double[] policies, int[] indexes, boolean isDirichlet) {
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
                log.warn("dirichlet: MAX policy: {} move:{}", maxPolicy, PolicyUtils.moveFromIndex(maxPolicyIndex));
                log.warn("dirichlet: MIN policy: {} move:{}", minPolicy, PolicyUtils.moveFromIndex(minPolicyIndex));
                log.warn("dirichlet: indexes: {} <-> {} : policies>0",
                        indexes.length,
                        Arrays.stream(policies).filter(policy -> policy > 0).count());

            }
        }
        return policies;
    }

    /**
     * <h3>Code from StackOverflow: Can a Java application detect that a debugger is attached?</h3>
     * @see <a href="https://stackoverflow.com/questions/5393403/can-a-java-application-detect-that-a-debugger-is-attached">stackoverflow.com</a>
     * @return true if a debugger is attached to the current JVM
     */
    public static boolean isDebuggerPresent() {
        // Get ahold of the Java Runtime Environment (JRE) management interface
        RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();

        // Get the command line arguments that we were originally passed in
        List<String> args = runtime.getInputArguments();

        // Check if the Java Debug Wire Protocol (JDWP) agent is used.
        // One of the items might contain something like "-agentlib:jdwp=transport=dt_socket,address=9009,server=y,suspend=n"
        // We're looking for the string "jdwp".
        boolean jdwpPresent = args.toString().contains("jdwp");

        return jdwpPresent;
    }
    public static int nbMaxBits(long number) {
        return (int) (Math.log(Long.highestOneBit(number)) / Math.log(2.0));
    }

    public static String toSha1(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return UUID.nameUUIDFromBytes(md.digest(text.getBytes())).toString();
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

