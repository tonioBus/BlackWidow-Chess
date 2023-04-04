package com.aquila.chess.strategy.mcts.utils;

public class ConvertValueOutput {

    /**
     * convert sigmoid output range [0,-1] to [-1,1] range
     * @param value
     * @return
     */
    public static double convertFromSigmoid(double value) {
        return value * 2 - 1;
    }

    /**
     * convert [-1,1] range to sigmoid output range [0,-1]
     * @param value
     * @return
     */
    public static double convertToSigmoid(double value) {
        return (value + 1) / 2;
    }
}
