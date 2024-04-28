package com.aquila.chess;

import com.aquila.chess.strategy.mcts.ResultGame;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import com.aquila.chess.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.shade.jackson.databind.ObjectMapper;
import org.nd4j.shade.protobuf.common.io.PatternFilenameFilter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

@Slf4j
public class TrainGame implements Serializable {

    static final long serialVersionUID = -2638786203240540104L;

    public enum MarshallingType {POJO, JSON}

    ;

    @Getter
    private int num;

    @Getter
    Double value = null;

    @Setter
    @Getter
    LinkedList<OneStepRecord> oneStepRecordList = new LinkedList<>();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static void createTrainDir(String trainDir) {
        Path path = Paths.get(trainDir);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileName(String trainDir, int num, MarshallingType marshallingType) {
        return String.format("%s/%s%s", trainDir, num, marshallingType == MarshallingType.JSON ? ".json" : "");
    }

    public static TrainGame load(String trainDir, int num, MarshallingType marshallingType) throws IOException, ClassNotFoundException {
        FileInputStream fileInputStream
                = new FileInputStream(getFileName(trainDir, num, marshallingType));
        TrainGame ret = null;
        switch (marshallingType) {
            case POJO -> {
                ObjectInputStream objectInputStream
                        = new ObjectInputStream(fileInputStream);
                ret = (TrainGame) objectInputStream.readObject();
                objectInputStream.close();
            }
            case JSON -> ret = objectMapper.readValue(fileInputStream, TrainGame.class);
        }
        ret.num = num;
        return ret;
    }

    public static boolean isCorrectFilename(final String filename, MarshallingType marshallingType) {
        return switch (marshallingType) {
            case POJO -> Utils.isInteger(filename);
            case JSON -> filename.endsWith(".json");
        };
    }

    public static int getNum(String filename, MarshallingType marshallingType) {
        Path path = Paths.get(filename);
        String gameName = path.getFileName().toString();
        return switch (marshallingType) {
            case POJO -> Integer.parseInt(gameName);
            case JSON -> Integer.parseInt(gameName.replaceAll(".json", ""));
        };
    }

    public static TrainGame load(File file, MarshallingType marshallingType) throws
            IOException, ClassNotFoundException {
        FileInputStream fileInputStream
                = new FileInputStream(file);
        TrainGame ret = null;
        switch (marshallingType) {
            case POJO -> {
                ObjectInputStream objectInputStream
                        = new ObjectInputStream(fileInputStream);
                ret = (TrainGame) objectInputStream.readObject();
                objectInputStream.close();
            }
            case JSON -> ret = objectMapper.readValue(fileInputStream, TrainGame.class);
        }
        ret.num = getNum(file.getName(), marshallingType);
        return ret;
    }

    /**
     * Save a new game in the given folder with the given name
     *
     * @param trainDir   the folder where to save the game
     * @param num        the game number
     * @param resultGame the result of the game
     * @return the saved file name
     * @throws IOException
     */
    public String save(String trainDir, int num, final ResultGame resultGame, MarshallingType marshallingType) throws
            IOException {
        this.value = resultGame.reward;
        return save(trainDir, num, marshallingType);
    }

    public String save(String trainDir, int num, MarshallingType marshallingType) throws IOException {
        createTrainDir(trainDir);
        String filename = getFileName(trainDir, num, marshallingType);
        FileOutputStream fileOutputStream
                = new FileOutputStream(filename);
        switch (marshallingType) {
            case POJO -> {
                ObjectOutputStream objectOutputStream
                        = new ObjectOutputStream(fileOutputStream);
                objectOutputStream.writeObject(this);
            }
            case JSON -> objectMapper.writeValue(fileOutputStream, this);
        }
        fileOutputStream.close();
        return filename;
    }

    public ResultGame getResultGame(final Game.GameStatus gameStatus) {
        return switch (gameStatus) {
            case IN_PROGRESS, PAT, DRAW_3, DRAW_50, DRAW_TOO_MUCH_STEPS, DRAW_NOT_ENOUGH_PIECES -> new ResultGame(1, 1);
            case WHITE_CHESSMATE -> new ResultGame(0, 1);
            case BLACK_CHESSMATE -> new ResultGame(1, 0);
            default -> null;
        };
    }

    public String saveBatch(String trainDir, Game.GameStatus gameStatus, MarshallingType marshallingType) throws
            IOException {
        ResultGame resultGame = getResultGame(gameStatus);
        final int numGames = maxGame(trainDir + "/", marshallingType) + 1;
        log.info("SAVING Batch (game number: {}) ... (do not stop the jvm)", numGames);
        log.info("Result: {}   Game size: {} inputsList(s)", resultGame.reward, getOneStepRecordList().size());
        final String filename = save(trainDir, numGames, resultGame, marshallingType);
        log.info("SAVE DONE in {}", filename);
        clear();
        return filename;
    }

    private int maxGame(String path, MarshallingType marshallingType) {
        File dataDirectory = new File(path);
        int max = 0;
        String suffix = marshallingType == MarshallingType.JSON ? ".json" : "";
        if (dataDirectory.canRead()) {
            for (File file : dataDirectory.listFiles(new PatternFilenameFilter("[0-9]+" + suffix))) {
                String fileName = file.getName();
                if (marshallingType == MarshallingType.JSON) fileName = fileName.replaceAll(".json", "");
                int currentNumber = Integer.valueOf(fileName).intValue();
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
