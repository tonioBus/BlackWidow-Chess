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
import java.util.Scanner;

public class MainFitNN {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";
    @SuppressWarnings("unused")
    static private final Logger logger = LoggerFactory.getLogger(MainFitNN.class);

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
        train("train", deepLearningWhite);
//        waitForKey();
        train("train.1080", deepLearningWhite);
//        waitForKey();
        train("train.grospc", deepLearningWhite);
//        waitForKey();
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
