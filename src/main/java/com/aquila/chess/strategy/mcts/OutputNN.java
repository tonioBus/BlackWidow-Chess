package com.aquila.chess.strategy.mcts;

import lombok.Getter;

import java.io.Serializable;

public class OutputNN implements Serializable {
    @Getter
    final protected double value[];

    @Getter
    final protected double[] policies;

    public OutputNN(double value[], double[] policies) {
        this.value = value;
        this.policies = policies;
    }

}
