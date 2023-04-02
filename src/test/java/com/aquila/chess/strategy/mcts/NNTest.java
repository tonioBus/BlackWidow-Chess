package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.inputs.InputsNNFactory;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.*;

public class NNTest implements INN {

    double step = 0.000001;
    private float value = 0.1F;

    private final Map<Integer, Double> offsets = new HashMap<>();

    @Override
    public void reset() {

    }

    @Override
    public void train(boolean train) {

    }

    @Override
    public void close() {

    }

    @Override
    public double getScore() {
        return 0;
    }

    @Override
    public void setUpdateLr(UpdateLr updateLr, int nbGames) {

    }

    @Override
    public void updateLr(int nbGames) {

    }

    @Override
    public void save() throws IOException {

    }

    @Override
    public void fit(float[][][][] inputs, float[][] policies, float[][] values) {

    }

    @Override
    public String getFilename() {
        return null;
    }

    @Override
    public double getLR() {
        return 0;
    }

    @Override
    public void setLR(double lr) {

    }

    @Override
    public synchronized List<OutputNN> outputs(float[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            this.value += step;
            float[] policies = new float[PolicyUtils.MAX_POLICY_INDEX];
            Arrays.fill(policies, 0.2F);
            for (Map.Entry<Integer, Double> entry : this.offsets.entrySet()) {
                policies[entry.getKey()] += entry.getValue();
            }
            ret.add(new OutputNN(nbIn[i][InputsNNFactory.PLANE_COLOR][0][0] == 1.0F ? value + 0.2F : value, policies));
        }
        return ret;
    }

    public void addIndexOffset(double offset, int... indexes) {
        for (int index : indexes) {
            this.offsets.put(index, offset);
        }
    }

    @Override
    public NeuralNetwork getNetwork() {
        return null;
    }

}
