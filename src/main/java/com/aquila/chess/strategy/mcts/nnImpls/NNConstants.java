package com.aquila.chess.strategy.mcts.nnImpls;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.OutputNN;
import com.aquila.chess.strategy.mcts.PolicyUtils;
import com.aquila.chess.strategy.mcts.UpdateLr;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.NeuralNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@Slf4j
public class NNConstants implements INN {

    static private final double valueRangeMin = -0.400;
    static private final double valueRangeMax = 0.400;
    static private final double policyRangeMin = 0.100;
    static private final double policyRangeMax = 0.500;
    private final Random randomGenerator = new Random();
    private double mediumValue;
    private double mediumPolicies;

    private final Map<Integer, Double> offsets = new HashMap<>();

    public NNConstants(long seed) {
        randomGenerator.setSeed(seed);
        reset();
    }

    @Override
    public void reset() {
        mediumValue = valueRangeMin + (valueRangeMax - valueRangeMin) * randomGenerator.nextDouble();
        mediumPolicies = policyRangeMin + (policyRangeMax - policyRangeMin) * randomGenerator.nextDouble();
        log.warn("mediumValue: {}", mediumValue);
        log.warn("mediumPolicies: {}", mediumPolicies);
    }

    @Override
    public void close() {
    }

    @Override
    public synchronized List<OutputNN> outputs(double[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            double value = mediumValue;
            double[] policies = new double[PolicyUtils.MAX_POLICY_INDEX];
            for (int policyIndex = 0; policyIndex < PolicyUtils.MAX_POLICY_INDEX; policyIndex++) {
                policies[policyIndex] = mediumPolicies;
                if (offsets.containsKey(policyIndex)) {
                    double offset = offsets.get(policyIndex);
                    policies[policyIndex] += offset;
                }
            }
            ret.add(new OutputNN(value, policies));
        }
        return ret;
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
    public void fit(double[][][][] inputs, double[][] policies, double[][] values) {
        throw new RuntimeException("fit not allow with this implementation");
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
    public NeuralNetwork getNetwork() {
        return null;
    }

    public void addIndexOffset(double offset, int... indexes) {
        for (int index : indexes) {
            this.offsets.put(index, offset);
        }
    }

    public void clearIndexOffset() {
        this.offsets.clear();
    }
}
