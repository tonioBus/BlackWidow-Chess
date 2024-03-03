package com.aquila.chess.strategy.mcts.nnImpls;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.OutputNN;
import com.aquila.chess.strategy.mcts.utils.ConvertValueOutput;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class NNConstants implements INN {

    static private final double valueRangeMin = -0.400F;
    static private final double valueRangeMax = 0.400F;
    static private final double policyRangeMin = 0.100F;
    static private final double policyRangeMax = 0.500F;
    final Random randomGenerator = new Random();
    double mediumValue;
    double mediumPolicies;

    protected final Map<Integer, Double> offsets = new HashMap<>();

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
    public void train(boolean train) {

    }

    @Override
    public void close() {
    }

    @Override
    public synchronized List<OutputNN> outputs(double[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            double value[] =  ConvertValueOutput.convertSimulProbabilitiesQValueToSofMax(mediumValue);
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

    @Deprecated
    public void addIndexOffset(double offset, String s, int... indexes) {
        for (int index : indexes) {
            this.offsets.put(index, offset);
        }
    }

    public void addIndexOffset(double offset, final String movesSz, final Board board) {
        Stream.of(movesSz.toLowerCase().split(";")).forEach(moveSz -> {
            String[] splittedMove = moveSz.split("-");
            String startSz = splittedMove[0];
            String endSz = splittedMove[1];
            Piece piece = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition(startSz));
            int index = PolicyUtils.indexFromMove(piece.getPieceType(), startSz, endSz);
            this.offsets.put(index, offset);
        });
    }

    public void addIndexOffset(double offset, final String movesSz, final Piece.PieceType pieceType) {
        Stream.of(movesSz.toLowerCase().split(";")).forEach(moveSz -> {
            String[] splittedMove = moveSz.split("-");
            String startSz = splittedMove[0];
            String endSz = splittedMove[1];
            int index = PolicyUtils.indexFromMove(pieceType, startSz, endSz);
            this.offsets.put(index, offset);
        });
    }

    public void clearIndexOffset() {
        this.offsets.clear();
    }

}
