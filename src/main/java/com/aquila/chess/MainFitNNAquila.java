package com.aquila.chess;

import com.aquila.chess.strategy.mcts.DeepLearningAGZ;
import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import picocli.CommandLine;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(
        name = "MainFitNNAquila",
        description = "Fit the NN using AQUILA inputs schema"
)
public class MainFitNNAquila implements Runnable {
    static private final String NN_REFERENCE = "../AQUILA_NN/NN.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";

    @CommandLine.Option(names = {"-td", "--trainDir"})
    private String[] trainDirs;

    private static void settingsCuda() {
        CudaEnvironment.getInstance().getConfiguration()
                // key option enabled
                .allowMultiGPU(false) //
                .setFirstMemory(AllocationStatus.HOST)
                .setAllocationModel(Configuration.AllocationModel.CACHE_ALL)
                .setMaximumDeviceMemoryUsed(0.90) //
                .setMemoryModel(Configuration.MemoryModel.IMMEDIATE) //
                // cross-device access is used for faster model averaging over pcie
                .allowCrossDeviceAccess(false) //
                .setNumberOfGcThreads(4)
                // .setMaximumBlockSize(-1)
                .setMaximumGridSize(256)
                // .setMaximumDeviceCacheableLength(8L * 1024 * 1024 * 1024L)  // (6L * 1024 * 1024 * 1024L) //
                // .setMaximumDeviceCache(8L * 1024 * 1024 * 1024L) //
                .setMaximumHostCacheableLength(-1) // (6L * 1024 * 1024 * 1024L) //
                //.setMaximumHostCache(8L * 1024 * 1024 * 1024L)
                .setNoGcWindowMs(100)
                .enableDebug(false)
                .setVerbose(false);
    }

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static void main(final String[] args) throws Exception {
        CommandLine.run(new MainFitNNAquila(), args);
    }

    private void checkArguments() {
        if (trainDirs == null || trainDirs.length == 0) {
            log.error("no train directory specified (-td)");
            System.exit(-1);
        }
        Arrays.stream(trainDirs).forEach(trainDir -> {
            if (!Files.isReadable(Path.of(trainDir))) {
                log.error("Can not access directory: {}", trainDir);
                System.exit(-1);
            }
        });
    }

    @Override
    public void run() {
        checkArguments();
        InputsManager inputsManager = new AquilaInputsManagerImpl();
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true, inputsManager.getNbFeaturesPlanes(), 15);
        settingsCuda();
        ((ComputationGraph) nnWhite.getNetwork()).getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.ENABLED);
        UpdateLr updateLr = nbGames -> 1.0e-4;
        nnWhite.setUpdateLr(updateLr, 1);
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ
                .builder()
                .nn(nnWhite)
                .train(true)
                .batchSize(10)
                .inputsManager(inputsManager)
                .build();
        Arrays.stream(trainDirs).forEach(trainDir -> {
            try {
                train(trainDir, deepLearningWhite);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
//        train("train-aquila", deepLearningWhite);
//        train("train-aquila-linux", deepLearningWhite);
//        train("train-aquila-rog", deepLearningWhite);
        try {
            deepLearningWhite.save();
        } catch (IOException e) {
            log.error("Error when saving NN", e);
        }
        log.info("Train done in directory:\n{}", Arrays.stream(trainDirs).collect(Collectors.joining("- ", "- ", "")));
    }

    private void waitForKey() {
        Scanner input = new Scanner(System.in);
        System.out.print("Press Enter to continue...");
        input.nextLine();
    }

    public void train(final String subDir, final DeepLearningAGZ deepLearningWhite) throws IOException, ClassNotFoundException {
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(subDir + "/" + TRAIN_SETTINGS));
        log.info("START MainFitNNAquila");
        int startGame = Integer.valueOf(appProps.getProperty("start.game"));
        int endGame = Integer.valueOf(appProps.getProperty("end.game"));
        log.info("startGame: {}", startGame);
        log.info("endGame: {}", endGame);
        int nbGames = trainGames(subDir, startGame, endGame, deepLearningWhite);
        log.info("{} -> Train {} games.", subDir, nbGames - startGame);
    }

    public int trainGames(String subDir, final int startGame, final int endGame, final DeepLearningAGZ deepLearningWhite) {
        log.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            log.info("load game:{}", numGame);
            try {
                TrainGame trainGame = TrainGame.load(subDir, numGame);
                deepLearningWhite.train(trainGame);
            } catch (Exception e) {
                log.error(String.format("Error for the training game: %s/%s", subDir, numGame), e);
                log.error("Stopping the training ... :(");
            }
        }
        return numGame;
    }

}
