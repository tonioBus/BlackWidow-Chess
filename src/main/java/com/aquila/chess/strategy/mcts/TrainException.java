package com.aquila.chess.strategy.mcts;

import lombok.Getter;

@Getter
public class TrainException extends Exception {

    final String label;

    public TrainException(final String msg, final String label) {
        super(msg);
        this.label = label;
    }
}
