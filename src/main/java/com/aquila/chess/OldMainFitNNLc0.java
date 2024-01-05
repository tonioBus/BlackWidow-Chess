package com.aquila.chess;

import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
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
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(
        name = "MainFitNNLc0",
        description = "Fit the NN using Lc0 inputs schema"
)
public class OldMainFitNNLc0 implements Runnable {
    static private final String NN_REFERENCE = "../AGZ_NN/AGZ.reference";
    public static final String TRAIN_SETTINGS = "train-settings.properties";

    @SuppressWarnings("unused")

    private HashMap<String, StatisticsFit> statistics = new HashMap<>();

    @CommandLine.Option(names = {"-f", "--filter"})
    private int[] filters;
    @CommandLine.Option(names = {"-uLr", "--updateLr"})
    private double updateLrConstant; // = 1.0e-4;

    @CommandLine.Option(names = {"-td", "--trainDir"})
    private String[] trainDirs = {
            "train-grospc",
            "train",
            "train-linux"
    };

    final UpdateLr updateLr = nbGames -> updateLrConstant;

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
        CommandLine cmd = new CommandLine(new OldMainFitNNLc0());
        cmd.execute(args);
    }

    private void checkArguments() {
        if (trainDirs == null || trainDirs.length == 0) {
            log.error("no train directory specified (-td)");
            System.exit(-1);
        }
        if (trainDirs != null && !trainDirs[0].equals("null")) {
            Arrays.stream(trainDirs).forEach(trainDir -> {
                if (!Files.isReadable(Path.of(trainDir))) {
                    log.error("Can not access directory: {}", trainDir);
                    System.exit(-1);
                }
            });
        }
    }

    @Override
    public void run() {
        checkArguments();
        InputsManager inputsManager = new Lc0InputsManagerImpl();
        INN nnWhite = new NNDeep4j(NN_REFERENCE, true, inputsManager.getNbFeaturesPlanes(), 20);
        settingsCuda();
        ((ComputationGraph) nnWhite.getNetwork()).getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.ENABLED);
        log.info("SET UPDATE LR:{}", updateLrConstant);
        nnWhite.setUpdateLr(updateLr, 1);
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ
                .builder()
                .nn(nnWhite)
                .train(true)
                .batchSize(10)
                .inputsManager(inputsManager)
                .build();
        boolean saveIt =true;
        if (trainDirs != null && !trainDirs[0].equals("null")) {
            for(String trainDir : trainDirs) {
                log.info("TRAIN DIR: {}", trainDir);
                try {
                    train(trainDir, deepLearningWhite);
                } catch (TrainException e) {
                    log.error("TrainException: stopping ...", e);
                    saveIt = false;
                    break;
                } catch (IOException | ClassNotFoundException e) {
                    log.error("Exception: stopping ...", e);
                    saveIt = false;
                    break;
                } catch (RuntimeException e) {
                    log.error("RuntimeException, we will close without saving", e);
                    saveIt = false;
                    break;
                }
            }
        }
        try {
            if (saveIt) {
                log.info("Saving NN: do not stop the JVM ...");
                ((ComputationGraph) nnWhite.getNetwork()).getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.NONE);
                deepLearningWhite.save();
            }
        } catch (IOException e) {
            log.error("Error when saving NN", e);
        }
        log.info("Training using UpdateLR:{}", updateLrConstant);
        log.info("Train done in directories:\n{}", Arrays.stream(trainDirs).collect(Collectors.joining("\n- ", "- ", "")));
        statistics.entrySet().forEach(entry -> {
            String subDir = entry.getKey();
            log.info("------------------------------------\nSUBDIR:{}\n{}", subDir, entry.getValue());
        });
        if (!saveIt) {
            log.error("!!! Error: nothing saved.");
            System.exit(-1);
        }
    }

    private static void waitForKey() {
        Scanner input = new Scanner(System.in);
        System.out.print("Press Enter to continue...");
        input.nextLine();
    }

    public void train(final String subDir, final DeepLearningAGZ deepLearningWhite) throws IOException, ClassNotFoundException, TrainException {
        Properties appProps = new Properties();
        String traingFile = subDir + "/" + TRAIN_SETTINGS;
        appProps.load(new FileInputStream(traingFile));
        log.info("START MainFitNNLc0");
        int startGame = 0;
        int endGame = 0;
        try {
            startGame = Integer.valueOf(appProps.getProperty("start.game").trim());
            endGame = Integer.valueOf(appProps.getProperty("end.game").trim());
        } catch (NumberFormatException e) {
            log.error("Exception", e);
            log.warn("Cannot used training file {}", traingFile);
            return;
        }
        StatisticsFit statisticsFit = new StatisticsFit(startGame, endGame);
        statistics.put(subDir, statisticsFit);
        log.info("startGame: {}", startGame);
        log.info("endGame: {}", endGame);
        int nbGames = trainGames(subDir, startGame, endGame, deepLearningWhite, statisticsFit);
        log.info("{} -> Train {} games.", subDir, nbGames - startGame);
    }

    public int trainGames(String subDir, final int startGame, final int endGame, final DeepLearningAGZ deepLearningWhite, StatisticsFit statisticsFit) throws TrainException {
        log.info("train games from {} to {}", startGame, endGame);
        int numGame;
        for (numGame = startGame; numGame <= endGame; numGame++) {
            String filename = String.format("%s/%s", subDir, numGame);
            log.info("load game:{}", filename);
            Thread.currentThread().setName(filename);
            try {
                TrainGame trainGame = TrainGame.load(subDir, numGame);
                if (filters == null || Arrays.stream(filters).mapToDouble(Double::valueOf).filter(filter -> filter == trainGame.getValue()).count() == 0)
                    deepLearningWhite.train(trainGame, statisticsFit);
                else statisticsFit.listFilteredTrain.add("" + numGame);
            } catch (TrainException e) {
                throw e;
            } catch (Exception e) {
                log.error(String.format("Error for the training game: %s/%s", subDir, numGame), e);
                log.error("Stopping this training ... :(");
                statisticsFit.listErrorTrain.add("" + numGame);
            }
        }
        return numGame;
    }

}
