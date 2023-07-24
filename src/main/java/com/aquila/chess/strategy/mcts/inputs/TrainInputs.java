package com.aquila.chess.strategy.mcts.inputs;

import lombok.Getter;

public class TrainInputs {

    int index = 0;

    @Getter
    final double[][][][] inputs;

    public TrainInputs(int batchSize) {
        this.inputs = new double[batchSize][][][];
    }

    public void add(final OneStepRecord oneStepRecord) {
        inputs[index] = oneStepRecord.inputs().inputs();
        index++;
    }

}
