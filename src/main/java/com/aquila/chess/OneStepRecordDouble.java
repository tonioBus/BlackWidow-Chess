package com.aquila.chess;

import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNNFloat;
import com.chess.engine.classic.Alliance;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @param inputs     Board inputs from 1 position -> double[12][][]
 * @param color2play
 * @param policies
 */
public record OneStepRecordDouble(InputsFullNN inputs,
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

    private static InputsFullNN convertInputFloat(InputsFullNNFloat inputs) {
        float[][][] inputSrc = inputs.inputs();
        double[][][] inputDest = new double[inputSrc.length][inputSrc[0].length][inputSrc[0][0].length];
        for (int i = 0; i < inputSrc.length; i++) {
            for (int j = 0; j < inputSrc[i].length; j++) {
                for (int k = 0; k < inputSrc[i][j].length; k++) {
                    inputDest[i][j][k] = inputSrc[i][j][k];
                }
            }
        }
        return new InputsFullNN(inputDest);
    }
}
