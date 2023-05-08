package com.aquila.chess;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.chess.engine.classic.Alliance;

import java.io.Serializable;
import java.util.HashMap;
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

    private static Map<Integer, Double> convertPoliciesFloat(Map<Integer, Float> policies) {
        Map<Integer, Double> ret = new HashMap<>();
        for (int key : policies.keySet()) {
            ret.put(key, Double.valueOf(policies.get(key)));
        }
        return ret;
    }

}
