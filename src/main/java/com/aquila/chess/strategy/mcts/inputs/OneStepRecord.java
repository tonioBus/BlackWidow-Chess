package com.aquila.chess.strategy.mcts.inputs;

import com.chess.engine.classic.Alliance;

import java.io.Serializable;
import java.util.Map;

/**
 * @param inputs     Board inputs from 1 position -> double[12][][]
 * @param color2play
 * @param policies
 */
public record OneStepRecord(InputsFullNN inputs,
                            String move,
                            Alliance color2play,
                            Map<Integer, Double> policies) implements Serializable {

}