package com.aquila.chess;

import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.lc0.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.InputsNNFactory;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@Slf4j
public class MainFitNNSpecificInputs {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    @SuppressWarnings("unused")
    static private final Logger logger = LoggerFactory.getLogger(MainFitNNSpecificInputs.class);

    final Board board;
    final Game game;
    final Collection<Move> moves;
    final MCTSGame mctsGame;
    final INN nnWhite;
    final DeepLearningAGZ deepLearningWhite;
    final Map<Integer, Double> policies = new HashMap<>();
    final Move move;

    public MainFitNNSpecificInputs() {
        board = Board.createStandardBoard();
        game = Game.builder().board(board).build();
        mctsGame = new MCTSGame(game);
        moves = game.board.getAllLegalMoves();
        nnWhite = new NNDeep4j(NN_REFERENCE, true);
        UpdateLr updateLr = nbGames -> 1e-4;
        nnWhite.setUpdateLr(updateLr, 1);
        deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        final double probability = 1 / moves.size();
        moves.forEach(m -> {
            int index = PolicyUtils.indexFromMove(m);
            policies.put(index, probability);
        });
        Optional<Move> optMove = moves.stream().filter(m -> m.toString().equals("h4")).findFirst();
        assert (optMove.isPresent());
        move = optMove.get();
    }

    public void displayValues(final String banner) {
        log.info("[{}] score:{}", banner, deepLearningWhite.getScore());
        InputsFullNN inputs = createInputs();
        double[][][][] nbIn = new double[1][][][];
        nbIn[0] = inputs.inputs();
        List<OutputNN> outputs = deepLearningWhite.getNn().outputs(nbIn, 1);
        OutputNN outputNN = outputs.get(0);
        log.info("[{}]  value:{}", banner, outputNN.getValue());
    }

    public InputsFullNN createInputs() {
        MCTSGame mctsGame = new MCTSGame(game);
        InputsFullNN inputs = InputsNNFactory.createInput(mctsGame, move, Alliance.WHITE);
        return inputs;
    }

    public TrainGame createTrainGame() {
        TrainGame trainGame = new TrainGame();
        InputsFullNN inputs = createInputs();
        OneStepRecord oneStepRecord = new OneStepRecord(inputs, move.toString(), Alliance.WHITE, policies);
        trainGame.add(oneStepRecord);
        trainGame.value = -1.0;
        return trainGame;
    }

    private void run() throws IOException {
        TrainGame trainGame = createTrainGame();
        displayValues("BEFORE");
        for (int i = 0; i < 40; i++) {
            deepLearningWhite.train(trainGame);
        }
        displayValues("AFTER");
        // waitForKey();
        deepLearningWhite.save();
    }

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static void main(final String[] args) throws Exception {
        MainFitNNSpecificInputs mainFitNNSpecificInputs = new MainFitNNSpecificInputs();
        mainFitNNSpecificInputs.run();
    }

    private void waitForKey() {
        Scanner input = new Scanner(System.in);
        log.info("Press Enter to continue...");
        input.nextLine();
    }

}
