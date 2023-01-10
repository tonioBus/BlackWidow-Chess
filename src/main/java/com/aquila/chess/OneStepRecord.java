package com.aquila.chess;

import com.chess.engine.classic.Alliance;
import lombok.Getter;

import java.io.Serializable;
import java.util.Map;

public class OneStepRecord implements Serializable {

    static final long serialVersionUID = -8476549465916278054L;

    @Getter
    private final Alliance color2play;

    /**
     * Board inputs from 1 position -> double[12][][]
     */
    @Getter
    private final double[][][] inputs;

    @Getter
    private final Map<Integer, Double> policies;

    /**
     *
     * @param inputs
     * @param color2play
     * @param policies
     */
    public OneStepRecord(final double[][][] inputs, final Alliance color2play, Map<Integer, Double> policies) {
        super();
        this.inputs = inputs;
        this.color2play = color2play;
        this.policies = policies;
    }

}
