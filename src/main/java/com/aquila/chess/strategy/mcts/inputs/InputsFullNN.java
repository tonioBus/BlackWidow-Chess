package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.utils.Utils;

import java.io.Serializable;

public record InputsFullNN(double[][][] inputs) implements Serializable {

    public InputsFullNN {
        if (inputs.length != INN.FEATURES_PLANES)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, INN.FEATURES_PLANES));
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("inputs[%d]\n", i));
            sb.append(Utils.displayBoard(inputs, i));
        }
        return sb.toString();
    }
}
