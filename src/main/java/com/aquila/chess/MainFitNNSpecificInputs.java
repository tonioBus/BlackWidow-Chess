package com.aquila.chess;

import com.aquila.chess.strategy.mcts.DeepLearningAGZ;
import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.aquila.chess.strategy.mcts.UpdateLr;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Slf4j
public class MainFitNNSpecificInputs {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";
    @SuppressWarnings("unused")
    static private final Logger logger = LoggerFactory.getLogger(MainFitNNSpecificInputs.class);

    public static void displayValues(final String banner, final DeepLearningAGZ deepLearning) {
        log.info("[{}] score:{}", banner, deepLearning.getScore());
        deepLearning.getNn().outputs();

    }

    public static InputsFullNN createInputs(final Game game, final Collection<Move> moves) {
        MCTSGame mctsGame = new MCTSGame(game);
        Optional<Move> move = moves.stream().filter(m -> m.toString().equals("h4")).findFirst();
        assert (move.isPresent());
        InputsFullNN inputs = InputsNNFactory.createInput(mctsGame, move.get(), Alliance.WHITE);
        return inputs;
    }

    public static TrainGame createInput() {
        TrainGame trainGame = new TrainGame();
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        Collection<Move> moves = game.board.getAllLegalMoves();
        InputsFullNN inputs = createInputs(game, moves);
        final Map<Integer, Double> policies = new HashMap<>();
        final double probability = 1 / moves.size();
        moves.forEach(m -> {
            int index = PolicyUtils.indexFromMove(m);
            policies.put(index, probability);
        });
        OneStepRecord oneStepRecord = new OneStepRecord(inputs, move.get().toString(), Alliance.WHITE, policies);
        trainGame.add(oneStepRecord);
        trainGame.value = -1.0;
        return trainGame;
    }

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static void main(final String[] args) throws Exception {
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true);
        UpdateLr updateLr = nbGames -> {
            return 1e-4;
        };
        nnWhite.setUpdateLr(updateLr, 1);


        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);

        TrainGame trainGame = createInput();
        deepLearningWhite.train(trainGame);
        waitForKey();
        deepLearningWhite.save();
    }

    private static void waitForKey() {
        Scanner input = new Scanner(System.in);
        System.out.print("Press Enter to continue...");
        input.nextLine();
    }

    public static void train(final String subDir, final DeepLearningAGZ deepLearningWhite) throws IOException, ClassNotFoundException {
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(subDir + "/" + TRAIN_SETTINGS));
        logger.info("START MainFitNN");
        int startGame = Integer.valueOf(appProps.getProperty("start.game"));
        int endGame = Integer.valueOf(appProps.getProperty("end.game"));
        logger.info("startGame: {}", startGame);
        logger.info("endGame: {}", endGame);
        int nbGames = trainGames(subDir, startGame, endGame, deepLearningWhite);
        logger.info("{} -> Train {} games.", subDir, nbGames - startGame);
    }

    public static int trainGames(String subDir, final int startGame, final int endGame, final DeepLearningAGZ deepLearningWhite) {
        logger.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            logger.info("load game:{}", numGame);
            try {
                TrainGame trainGame = TrainGame.load(subDir, numGame);
                deepLearningWhite.train(trainGame);
            } catch (Exception e) {
                logger.error("Error for the training game: " + numGame, e);
            }
        }
        return numGame;
    }

}
