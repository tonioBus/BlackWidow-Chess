package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.OneStepRecordFloat;
import lombok.Getter;

public class BatchInputsNN {

    int index = 0;

    @Getter
    final double[][][][] inputs;

    public BatchInputsNN(int batchSize) {
        this.inputs = new double[batchSize][][][];
    }

    public void add(final OneStepRecordFloat oneStepRecord) {
        inputs[index] = oneStepRecord.inputs().inputs();
        index++;
    }

}
