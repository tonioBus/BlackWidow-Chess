package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;

public record Lc0InputsFullNN(double[][][] inputs) implements InputsFullNN {

    public Lc0InputsFullNN {
        if (inputs.length != Lc0InputsManagerImpl.FEATURES_PLANES)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, Lc0InputsManagerImpl.FEATURES_PLANES));
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("inputs[%d]\n", i));
            sb.append(Lc0Utils.displayBoard(inputs, i));
        }
        return sb.toString();
    }
}