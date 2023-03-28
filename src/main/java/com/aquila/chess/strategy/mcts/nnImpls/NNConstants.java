package com.aquila.chess.strategy.mcts.nnImpls;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.OutputNN;
import com.aquila.chess.strategy.mcts.PolicyUtils;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class NNConstants implements INN {

    static private final float valueRangeMin = -0.400F;
    static private final float valueRangeMax = 0.400F;
    static private final float policyRangeMin = 0.100F;
    static private final float policyRangeMax = 0.500F;
    final Random randomGenerator = new Random();
    float mediumValue;
    float mediumPolicies;

    protected final Map<Integer, Float> offsets = new HashMap<>();

    public NNConstants(long seed) {
        randomGenerator.setSeed(seed);
        reset();
    }

    @Override
    public void reset() {
        mediumValue = valueRangeMin + (valueRangeMax - valueRangeMin) * randomGenerator.nextFloat();
        mediumPolicies = policyRangeMin + (policyRangeMax - policyRangeMin) * randomGenerator.nextFloat();
        log.warn("mediumValue: {}", mediumValue);
        log.warn("mediumPolicies: {}", mediumPolicies);
    }

    @Override
    public void train(boolean train) {

    }

    @Override
    public void close() {
    }

    @Override
    public synchronized List<OutputNN> outputs(float[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            float value = mediumValue;
            float[] policies = new float[PolicyUtils.MAX_POLICY_INDEX];
            for (int policyIndex = 0; policyIndex < PolicyUtils.MAX_POLICY_INDEX; policyIndex++) {
                policies[policyIndex] = mediumPolicies;
                if (offsets.containsKey(policyIndex)) {
                    float offset = offsets.get(policyIndex);
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
    public void fit(float[][][][] inputs, float[][] policies, float[][] values) {
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

    @Deprecated
    public void addIndexOffset(float offset, String s, int... indexes) {
        for (int index : indexes) {
            this.offsets.put(index, offset);
        }
    }

    public void addIndexOffset(float offset, final String movesSz, final Board board) {
        Stream.of(movesSz.toLowerCase().split(";")).forEach(moveSz -> {
            String[] splittedMove = moveSz.split("-");
            String startSz = splittedMove[0];
            String endSz = splittedMove[1];
            Piece piece = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition(startSz));
            int index = PolicyUtils.indexFromMove(piece, startSz, endSz);
            this.offsets.put(index, offset);
        });
    }

    public void addIndexOffset(float offset, final String movesSz, final Piece piece) {
        Stream.of(movesSz.toLowerCase().split(";")).forEach(moveSz -> {
            String[] splittedMove = moveSz.split("-");
            String startSz = splittedMove[0];
            String endSz = splittedMove[1];
            int index = PolicyUtils.indexFromMove(piece, startSz, endSz);
            this.offsets.put(index, offset);
        });
    }

    public void clearIndexOffset() {
        this.offsets.clear();
    }

}
