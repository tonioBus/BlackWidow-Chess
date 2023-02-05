package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.OneStepRecord;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class BatchInputsNN {

    int index = 0;

    @Getter
    final double[][][][] inputs;

    public BatchInputsNN(int batchSize) {
        this.inputs = new double[batchSize][][][];
    }

    public void add(final OneStepRecord oneStepRecord) {
        inputs[index] = oneStepRecord.inputs().inputs();
    }

}
