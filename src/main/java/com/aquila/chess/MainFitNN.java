package com.aquila.chess;

import com.aquila.chess.strategy.mcts.DeepLearningAGZ;
import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class MainFitNN {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train/train-settings.properties";
    @SuppressWarnings("unused")
    static private final Logger logger = LoggerFactory.getLogger(MainFitNN.class);

    public static void main(final String[] args) throws Exception {
        UpdateLr updateLr = MainTrainingAGZ.updateLr;
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(TRAIN_SETTINGS));
        logger.info("START MainFitNN");
        int startGame = Integer.valueOf(appProps.getProperty("start.game"));
        int endGame = Integer.valueOf(appProps.getProperty("end.game"));
        logger.info("startGame: {}", startGame);
        logger.info("endGame: {}", endGame);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true);
        nnWhite.setUpdateLr(updateLr, startGame);
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        int nbGames = trainGames(startGame, endGame, updateLr, deepLearningWhite);
        logger.info("Train {} games.", nbGames - startGame);
    }

    public static int trainGames(final int startGame, final int endGame, final UpdateLr updateLr, final DeepLearningAGZ deepLearningWhite) throws IOException {
        logger.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            deepLearningWhite.setUpdateLr(updateLr, numGame);
            logger.info("load game:{}", numGame);
            TrainGame trainGame = null;
            try {
                trainGame = TrainGame.load(numGame);
                deepLearningWhite.train(trainGame);
            } catch (IOException | ClassNotFoundException e) {
                logger.error("Error for the training game: " + numGame, e);
            }
        }
        deepLearningWhite.save();
        return numGame;
    }

}
