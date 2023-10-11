package com.aquila.chess;

import com.aquila.chess.strategy.mcts.ResultGame;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.shade.protobuf.common.io.PatternFilenameFilter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

@Slf4j
public class TrainGame implements Serializable {

    static final long serialVersionUID = -2638786203240540104L;

    @Getter
    Double value = null;

    @Setter
    @Getter
    LinkedList<OneStepRecord> oneStepRecordList = new LinkedList<>();

    private static void createTrainDir(String trainDir) {
        Path path = Paths.get(trainDir);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TrainGame load(String trainDir, int num) throws IOException, ClassNotFoundException {
        FileInputStream fileInputStream
                = new FileInputStream(trainDir + "/" + num);
        ObjectInputStream objectInputStream
                = new ObjectInputStream(fileInputStream);
        TrainGame ret = (TrainGame) objectInputStream.readObject();
        objectInputStream.close();
        return ret;
    }

    /**
     * Save a new game in the given folder with the given name
     * @param trainDir the folder where to save the game
     * @param num the game number
     * @param resultGame the result of the game
     * @return the saved file name
     * @throws IOException
     */
    public String save(String trainDir, int num, final ResultGame resultGame) throws IOException {
        createTrainDir(trainDir);
        this.value = resultGame.reward;
        String filename = trainDir + "/" + num;
        FileOutputStream fileOutputStream
                = new FileOutputStream(filename);
        ObjectOutputStream objectOutputStream
                = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(this);
        objectOutputStream.flush();
        objectOutputStream.close();
        return filename;
    }

    public ResultGame getResultGame(final Game.GameStatus gameStatus) {
        return switch (gameStatus) {
            case IN_PROGRESS, PAT, DRAW_3, DRAW_50, DRAW_300, DRAW_NOT_ENOUGH_PIECES -> new ResultGame(1, 1);
            case WHITE_CHESSMATE -> new ResultGame(0, 1);
            case BLACK_CHESSMATE -> new ResultGame(1, 0);
            default -> null;
        };
    }

    public String saveBatch(String trainDir, Game.GameStatus gameStatus) throws IOException {
        ResultGame resultGame = getResultGame(gameStatus);
        final int numGames = maxGame(trainDir + "/") + 1;
        log.info("SAVING Batch (game number: {}) ... (do not stop the jvm)", numGames);
        log.info("Result: {}   Game size: {} inputsList(s)", resultGame.reward, getOneStepRecordList().size());
        final String filename = save(trainDir, numGames, resultGame);
        log.info("SAVE DONE in {}", filename);
        clear();
        return filename;
    }

    private int maxGame(String path) {
        File dataDirectory = new File(path);
        int max = 0;
        if (dataDirectory.canRead()) {
            for (File file : dataDirectory.listFiles(new PatternFilenameFilter("[0-9]+"))) {
                int currentNumber = Integer.valueOf(file.getName()).intValue();
                if (currentNumber > max) max = currentNumber;
            }
        }
        return max;
    }

    public void add(final OneStepRecord oneStepRecord) {
        this.oneStepRecordList.add(oneStepRecord);
    }

    public void clear() {
        this.oneStepRecordList.clear();
        value = null;
    }

}
