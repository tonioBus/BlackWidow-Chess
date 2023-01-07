/**
 *
 */
package com.aquila.chess.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import umontreal.ssj.randvarmulti.DirichletGen;
import umontreal.ssj.rng.MRG32k3a;
import umontreal.ssj.rng.RandomStream;

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
                    policies[i] = newP;
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

}

