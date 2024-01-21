package com.aquila.chess.strategy.mcts;

import lombok.Getter;

public class TrainException extends Exception {

    @Getter
    final String label;

    public TrainException(final String msg, final String label) {
        super(msg);
        this.label = label;
    }
}
