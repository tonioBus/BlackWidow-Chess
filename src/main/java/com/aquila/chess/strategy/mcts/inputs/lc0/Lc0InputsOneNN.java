package com.aquila.chess.strategy.mcts.inputs.lc0;

public record Lc0InputsOneNN(double[][][] inputs) {

    public Lc0InputsOneNN {
        if (inputs.length != Lc0InputsManagerImpl.SIZE_POSITION)
            throw new RuntimeException(String.format("Length error. Argument:%d expected:%d", inputs.length, Lc0InputsManagerImpl.SIZE_POSITION));
    }

    @Override
    public String toString() {
        return Lc0Utils.displayBoard(inputs, 0);
    }

    public boolean equals(Lc0InputsOneNN lc0InputsOneNN) {
        if (lc0InputsOneNN == null) return false;
        return Lc0Utils.displayBoard(inputs(), 0)
                .equals(Lc0Utils.displayBoard(lc0InputsOneNN.inputs(), 0));
    }
}
