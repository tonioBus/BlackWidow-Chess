package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.OneStepRecord;
import lombok.Getter;

public class BatchInputsNN {

    int index = 0;

    @Getter
    final float[][][][] inputs;

    public BatchInputsNN(int batchSize) {
        this.inputs = new float[batchSize][][][];
    }

    public void add(final OneStepRecord oneStepRecord) {
        inputs[index] = oneStepRecord.inputs().inputs();
        index++;
    }

}
