package com.aquila.chess.strategy.mcts;

import lombok.Getter;

import java.io.Serializable;

public class OutputNN implements Serializable {
    @Getter
    protected float value;

    @Getter
    protected float[] policies;

    protected OutputNN() {}

    public OutputNN(float value, float[] policies) {
        this.value = value;
        this.policies = policies;
    }

}
