package com.aquila.chess.strategy.mcts.inputs;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.utils.Utils;

import java.io.Serializable;

public record InputsFullNNFloat(float[][][] inputs) implements Serializable {

    public InputsFullNNFloat {
        if (inputs.length != INN.FEATURES_PLANES)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, INN.FEATURES_PLANES));
    }

}
