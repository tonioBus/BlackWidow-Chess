package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.utils.Utils;

public record InputsOneNN(double[][][] inputs) {

    public InputsOneNN {
        if (inputs.length != INN.SIZE_POSITION)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, INN.SIZE_POSITION));
    }

    @Override
    public String toString() {
        return Lc0Utils.displayBoard(inputs, 0);
    }

    public boolean equals(InputsOneNN inputsOneNN) {
        if (inputsOneNN == null) return false;
        return Lc0Utils.displayBoard(inputs(), 0)
                .equals(Lc0Utils.displayBoard(inputsOneNN.inputs(), 0));
    }
}
