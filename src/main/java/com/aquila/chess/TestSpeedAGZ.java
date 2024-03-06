package com.aquila.chess;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.utils.ConvertValueOutput;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
public class TestSpeedAGZ {

    static private final String NN_TEST = "../AGZ_NN/AGZ.reference";

    public void run() {
        final Board board = Board.createStandardBoard();
        final Lc0InputsManagerImpl inputManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().inputsManager(inputManager).board(board).build();

        INN nnWhite = new NNDeep4j(NN_TEST, false, inputManager.getNbFeaturesPlanes(), 20);
        ComputationGraph computationGraph = (ComputationGraph) nnWhite.getNetwork();
        long start, end, delay;

        start = System.currentTimeMillis();
        int length = 1;
        final var nbIn = new double[length][inputManager.getNbFeaturesPlanes()][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        Lc0InputsFullNN inputsFullNN = inputManager.createInputs(
                new InputRecord(
                        game,
                        game.getMoves(),
                        null,
                        Alliance.WHITE));
        for (int i = 0; i < length; i++) {
            System.arraycopy(inputsFullNN.inputs(), 0, nbIn[i], 0, inputManager.getNbFeaturesPlanes());
        }
        INDArray inputsArray = Nd4j.create(nbIn);
        end = System.currentTimeMillis();
        delay = end - start;
        log.info("delay:{} ms / {} s", delay, ((double) delay / 1000));

        start = System.currentTimeMillis();
        INDArray[] outputs = computationGraph.output(inputsArray);
        float value = outputs[1].getColumn(0).getFloat(0);
        float[] policies = outputs[0].getRow(0).toFloatVector();
        // log.info("PoliciesSum:{}", Arrays.stream(policies).sum());
        log.info("Policies:{} {} {} {} {} {} {} {} {} {}"
                , policies[0]
                , policies[1]
                , policies[2]
                , policies[3]
                , policies[4]
                , policies[5]
                , policies[6]
                , policies[7]
                , policies[8]
                , policies[9]
        );
        log.info("value:{}", value);
        end = System.currentTimeMillis();
        delay = end - start;
        log.info("delay:{} ms / {} s", delay, ((double) delay / 1000));
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        TestSpeedAGZ testSpeedAGZ = new TestSpeedAGZ();
        testSpeedAGZ.run();
    }
}
