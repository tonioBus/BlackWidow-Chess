package com.aquila.chess;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNNFloat;
import com.chess.engine.classic.Alliance;

import java.io.Serializable;
import java.util.Map;

/**
 * @param inputs     Board inputs from 1 position -> double[12][][]
 * @param color2play
 * @param policies
 */
public record OneStepRecordFloat(InputsFullNNFloat inputs,
                                 String move,
                                 Alliance color2play,
                                 Map<Integer, Float> policies) implements Serializable {

}
