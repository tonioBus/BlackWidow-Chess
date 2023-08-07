package com.aquila.chess;

import com.aquila.chess.strategy.mcts.DeepLearningAGZ;
import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

@Slf4j
public class MainFitNNAquila {
    static private final String NN_REFERENCE = "../AQUILA_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static void main(final String[] args) throws Exception {
        @NonNull InputsManager inputsManager = new AquilaInputsManagerImpl();
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true, inputsManager.getNbFeaturesPlanes(), 15);
        UpdateLr updateLr = nbGames -> 1.0e-4;
        nnWhite.setUpdateLr(updateLr, 1);
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ
                .builder()
                .nn(nnWhite)
                .train(true)
                .inputsManager(inputsManager)
                .build();
        train("train-aquila-rog", deepLearningWhite);
//        waitForKey();
        train("train-aquila", deepLearningWhite);
//        waitForKey();
        train("train-aquila-grospc", deepLearningWhite);
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
        log.info("START MainFitNNLc0");
        int startGame = Integer.valueOf(appProps.getProperty("start.game"));
        int endGame = Integer.valueOf(appProps.getProperty("end.game"));
        log.info("startGame: {}", startGame);
        log.info("endGame: {}", endGame);
        int nbGames = trainGames(subDir, startGame, endGame, deepLearningWhite);
        log.info("{} -> Train {} games.", subDir, nbGames - startGame);
    }

    public static int trainGames(String subDir, final int startGame, final int endGame, final DeepLearningAGZ deepLearningWhite) {
        log.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            log.info("load game:{}", numGame);
            try {
                TrainGame trainGame = TrainGame.load(subDir, numGame);
                deepLearningWhite.train(trainGame);
            } catch (Exception e) {
                log.error("Error for the training game: " + numGame, e);
            }
        }
        return numGame;
    }

}
