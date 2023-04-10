package com.aquila.chess.strategy.mcts.utils;

public class ConvertValueOutput {

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
//        return (value + 1) / 2;
    }
}
