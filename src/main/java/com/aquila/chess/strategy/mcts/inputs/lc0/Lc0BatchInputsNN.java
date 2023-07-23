package com.aquila.chess.strategy.mcts.inputs.lc0;

import lombok.Getter;

public class Lc0BatchInputsNN {

    int index = 0;

    @Getter
    final double[][][][] inputs;

    public Lc0BatchInputsNN(int batchSize) {
        this.inputs = new double[batchSize][][][];
    }

    public void add(final Lc0OneStepRecord lc0OneStepRecord) {
        inputs[index] = lc0OneStepRecord.inputs().inputs();
        index++;
    }

}
