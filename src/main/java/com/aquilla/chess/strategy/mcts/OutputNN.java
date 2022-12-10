package com.aquilla.chess.strategy.mcts;

import lombok.Getter;

import java.io.Serializable;

public class OutputNN implements Serializable {
    @Getter
    protected double value;

    @Getter
    protected double[] policies;

    protected OutputNN() {}

    public OutputNN(double value, double[] policies) {
        this.value = value;
        this.policies = policies;
    }

}
