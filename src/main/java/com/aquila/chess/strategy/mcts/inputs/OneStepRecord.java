package com.aquila.chess.strategy.mcts.inputs;

import com.chess.engine.classic.Alliance;

import java.io.Serializable;
import java.util.Map;

/**
 * @param inputs     Board inputs from 1 position -> double[12][][]
 * @param moveColor
 * @param policies
 */
public record OneStepRecord(InputsFullNN inputs,
                            String move,
                            Alliance moveColor,
                            Map<Integer, Double> policies) implements Serializable {
}
