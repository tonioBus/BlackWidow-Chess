package com.aquila.chess;

import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

@Slf4j
public class MainFitNNSpecificInputs {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    @SuppressWarnings("unused")

    final Board board;
    final Game game;
    final Collection<Move> moves;
    final MCTSGame mctsGame;
    final INN nnWhite;
    final DeepLearningAGZ deepLearningWhite;
    final Map<Integer, Double> policies = new HashMap<>();

    InputsManager inputsManager = new Lc0InputsManagerImpl();
//    final Move move;

    public MainFitNNSpecificInputs() {
        board = Board.createStandardBoard();
        game = Game.builder().board(board).inputsManager(inputsManager).build();
        mctsGame = new MCTSGame(game);
        moves = game.board.getAllLegalMoves();
        nnWhite = new NNDeep4j(NN_REFERENCE, true, inputsManager.getNbFeaturesPlanes(), 20);
        UpdateLr updateLr = nbGames -> 1e-4;
        nnWhite.setUpdateLr(updateLr, 1);
        deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnWhite)
                .inputsManager(inputsManager)
                .train(true)
                .build();
        final double probability = 1.0 / moves.size();
        moves.forEach(m -> {
            int index = PolicyUtils.indexFromMove(m);
            policies.put(index, probability);
        });
    }

    public void displayValues(final String banner) {
        log.info("[{}] score:{}", banner, deepLearningWhite.getScore());
        final int len = moves.size();
        double[][][][] nbIn = new double[len][][][];
        int i;
        Iterator<Move> iterMove = moves.iterator();
        for (i = 0; i < len; i++) {
            Move m = iterMove.next();
            InputsFullNN inputs = createInputs(m);
            nbIn[i] = inputs.inputs();
        }
        List<OutputNN> outputs = deepLearningWhite.getNn().outputs(nbIn, len);
        iterMove = moves.iterator();
        for (i = 0; i < len; i++) {
            Move m = iterMove.next();
            log.info("[{}] move:{} value:{}", banner, m, outputs.get(i).getValue());
        }
    }

    public InputsFullNN createInputs(final Move move) {
        InputRecord inputRecord = new InputRecord(game, new ArrayList<>(), move, Alliance.WHITE);
        return inputsManager.createInputs(inputRecord);
    }

    public TrainGame createTrainGame(int nbIterations) {
        TrainGame trainGame = new TrainGame();
        InputsFullNN inputs = createInputs(null);
        OneStepRecord oneStepRecord = new OneStepRecord(inputs, "INIT", Alliance.BLACK, policies);
        trainGame.add(oneStepRecord);
        oneStepRecord = new OneStepRecord(inputs, "INIT", Alliance.WHITE, policies);
        trainGame.add(oneStepRecord);
        final int len = moves.size();
        for (int iteration = 0; iteration < nbIterations; iteration++) {
            Iterator<Move> iterMove = moves.iterator();
            for (int i = 0; i < len; i++) {
                Move m = iterMove.next();
                inputs = createInputs(m);
                oneStepRecord = new OneStepRecord(inputs, m.toString(), Alliance.WHITE, policies);
                trainGame.add(oneStepRecord);
            }
        }
        trainGame.value = 0.0;
        return trainGame;
    }

    private void run() throws IOException, TrainException {
        TrainGame trainGame = createTrainGame(500);
        displayValues("BEFORE");
        deepLearningWhite.train(trainGame, 40, null);
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
