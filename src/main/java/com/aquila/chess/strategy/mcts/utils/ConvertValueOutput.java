package com.aquila.chess.strategy.mcts.utils;

import java.util.stream.DoubleStream;

public class ConvertValueOutput {

    public static final int INDEX_LOSS = 0;
    public static final int INDEX_DRAWN = 1;
    public static final int INDEX_WIN = 2;

    /**
     * convert sigmoid output range [0,-1] to [-1,1] range
     *
     * @param value
     * @return
     */
    public static double convertFromSigmoid(double value) {
        return value * 2 - 1;
    }

    /**
     * convert [-1,1] range to sigmoid output range [0,-1]
     *
     * @param value
     * @return
     */
    public static double convertTrainValueToSigmoid(double value) {
        return switch ((int) value) {
            case -1 -> 0;
            case 0 -> 0.5;
            case 1 -> 1;
            default -> throw new IllegalStateException("Unexpected value: " + (int) value);
        };
    }

    public static double[] convertTrainQValueToSofMax(double value) {
        double[] ret = new double[3];
        switch ((int) value) {
            case -1 -> ret[INDEX_LOSS] = 1;
            case 0 -> ret[INDEX_DRAWN] = 1;
            case 1 -> ret[INDEX_WIN] = 1;
            default -> throw new IllegalStateException("Unexpected value: " + (int) value);
        }
        return ret;
    }

    static final double ONE_THIRD = 1.0 / 3.0;

    public static double[] convertSimulProbabilitiesQValueToSofMax(double value) {
        double[] ret = new double[3];
        if (value < -ONE_THIRD) ret[INDEX_LOSS] = Math.abs(value);
        else if (value < ONE_THIRD) ret[INDEX_DRAWN] = Math.abs(value);
        else ret[INDEX_WIN] = Math.abs(value);
        return ret;
    }

    public static double softMax2QValue(double[] softmax) {
        double sum = DoubleStream.of(softmax).sum();
        double normalisedSoftmax[] = DoubleStream.of(softmax).map(value -> value / sum).toArray();
        return normalisedSoftmax[INDEX_WIN] - normalisedSoftmax[INDEX_LOSS];
    }
}
