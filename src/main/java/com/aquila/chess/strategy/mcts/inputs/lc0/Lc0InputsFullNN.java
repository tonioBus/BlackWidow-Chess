package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;

public record Lc0InputsFullNN(double[][][] inputs) implements InputsFullNN {

    public Lc0InputsFullNN {
        if (inputs.length != Lc0InputsManagerImpl.FEATURES_PLANES)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, Lc0InputsManagerImpl.FEATURES_PLANES));
    }

    @Override
    public String toString() {
        if (inputs == null) return "null";
        StringBuffer sb = new StringBuffer();
        String color = inputs[Lc0InputsManagerImpl.PLANE_COLOR][0][0] == 1.0 ? "BLACK" : "WHITE";
        for (int i = 0; i < 8; i++) {
            sb.append(String.format("inputs[%d]\n", i));
            sb.append(Lc0Utils.displayBoard(inputs, i));
        }
        return "[" + color + "]:\n" + sb;
    }
}