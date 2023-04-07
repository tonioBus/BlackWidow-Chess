package com.aquila.chess;

import com.aquila.chess.strategy.mcts.DeepLearningAGZ;
import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

public class MainFitNN {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";
    @SuppressWarnings("unused")
    static private final Logger logger = LoggerFactory.getLogger(MainFitNN.class);

    public static void main(final String[] args) throws Exception {
        train("train");
        waitForKey();
        train("train.1080");
        waitForKey();
        train("train.grospc");
        waitForKey();
    }

    private static void waitForKey() {
        Scanner input = new Scanner(System.in);
        System.out.print("Press Enter to quit...");
        input.nextLine();
    }

    public static void train(String subDir) throws IOException, ClassNotFoundException {
        UpdateLr updateLr = MainTrainingAGZ.updateLr;
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(subDir + "/" + TRAIN_SETTINGS));
        logger.info("START MainFitNN");
        int startGame = Integer.valueOf(appProps.getProperty("start.game"));
        int endGame = Integer.valueOf(appProps.getProperty("end.game"));
        logger.info("startGame: {}", startGame);
        logger.info("endGame: {}", endGame);
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true);
        nnWhite.setUpdateLr(updateLr, startGame);
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nnWhite, true);
        int nbGames = trainGames(subDir, startGame, endGame, updateLr, deepLearningWhite);
        logger.info("Train {} games.", nbGames - startGame);
    }

    public static int trainGames(String subDir, final int startGame, final int endGame, final UpdateLr updateLr, final DeepLearningAGZ deepLearningWhite) throws IOException, ClassNotFoundException {
        logger.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            deepLearningWhite.setUpdateLr(updateLr, numGame);
            logger.info("load game:{}", numGame);
            try {
                TrainGameDouble trainGame = TrainGameDouble.load(subDir, numGame);
                deepLearningWhite.train(trainGame);
            } catch (Exception e) {
                logger.error("Error for the training game: " + numGame, e);
            }
        }
        deepLearningWhite.save();
        return numGame;
    }

}
