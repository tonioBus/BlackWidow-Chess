package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import lombok.extern.slf4j.Slf4j;

import static com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl.FEATURES_PLANES;
import static com.aquila.chess.strategy.mcts.inputs.aquila.AquilaUtils.displayBoard;
import static com.aquila.chess.strategy.mcts.inputs.aquila.AquilaUtils.displaySimplePlaneBoard;

@Slf4j
public record AquilaInputsFullNN(double[][][] inputs) implements InputsFullNN {

    public AquilaInputsFullNN {
        if (inputs.length != FEATURES_PLANES)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, Lc0InputsManagerImpl.FEATURES_PLANES));
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("inputs\n");
        sb.append(displayBoard(inputs, 0));
        sb.append("\nmoves\n");
        sb.append(displayBoard(inputs, 12));
        sb.append("\nattacks\n");
        sb.append(displayBoard(inputs, 24));
        sb.append("\nKing Liberty\n");
        sb.append(displaySimplePlaneBoard(inputs, 36));
        sb.append("\nPawn moves\n");
        sb.append(displaySimplePlaneBoard(inputs, 38));
        return sb.toString();
    }

}
