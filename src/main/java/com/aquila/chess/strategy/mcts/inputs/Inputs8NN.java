package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.utils.Utils;

@Deprecated
public record Inputs8NN(double[][][] inputs) {

    public Inputs8NN {
        if (inputs.length != INN.SIZE_POSITION * 8)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, INN.SIZE_POSITION * 8));
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("inputs[%d]\n", i));
            sb.append(Utils.displayBoard(inputs, i));
        }
        return sb.toString();
    }
}
