package com.aquila.chess;

import com.aquila.chess.strategy.mcts.ResultGame;
import com.aquila.chess.strategy.mcts.inputs.OneStepRecord;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

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

    public void add(final OneStepRecord oneStepRecord) {
        this.oneStepRecordList.add(oneStepRecord);
    }

    public void clear() {
        this.oneStepRecordList.clear();
        value = null;
    }

}
