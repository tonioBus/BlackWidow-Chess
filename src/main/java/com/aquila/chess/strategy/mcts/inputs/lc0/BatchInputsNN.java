package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.OneStepRecord;
import lombok.Getter;

public class BatchInputsNN {

    int index = 0;

    @Getter
    final double[][][][] inputs;

    public BatchInputsNN(int batchSize) {
        this.inputs = new double[batchSize][][][];
    }

    public void add(final OneStepRecord oneStepRecord) {
        inputs[index] = oneStepRecord.inputs().inputs();
        index++;
    }

}