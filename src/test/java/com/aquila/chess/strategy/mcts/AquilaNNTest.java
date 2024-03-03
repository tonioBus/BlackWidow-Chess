package com.aquila.chess.strategy.mcts;

import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.utils.ConvertValueOutput;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.*;

public class AquilaNNTest implements INN {

    double step = 0.000001;
    private double value = 0.1;

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
        return 0.0;
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
    public void fit(double[][][][] inputs, double[][] policies, double[][] values) {

    }

    @Override
    public String getFilename() {
        return null;
    }

    @Override
    public double getLR() {
        return 0.0;
    }

    @Override
    public void setLR(double lr) {

    }

    @Override
    public synchronized List<OutputNN> outputs(double[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            this.value += step;
            double[] policies = new double[PolicyUtils.MAX_POLICY_INDEX];
            Arrays.fill(policies, 0.2);
            for (Map.Entry<Integer, Double> entry : this.offsets.entrySet()) {
                policies[entry.getKey()] += entry.getValue();
            }
            ret.add(new OutputNN(nbIn[i][AquilaInputsManagerImpl.PLANE_COLOR][0]= ConvertValueOutput.convertSimulProbabilitiesQValueToSofMax(value), policies));
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
