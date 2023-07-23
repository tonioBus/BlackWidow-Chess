package com.aquila.chess;

import com.aquila.chess.strategy.mcts.ResultGame;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0OneStepRecord;
import lombok.Getter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

public class TrainGame implements Serializable {

    static final long serialVersionUID = -2638786203240540104L;

    static {
        Path path = Paths.get("train/");

        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Getter
    Double value = null;
    @Getter
    final LinkedList<Lc0OneStepRecord> lc0OneStepRecordList = new LinkedList<>();

    public TrainGame() {
    }

    public static TrainGame load(String subDir, int num) throws IOException, ClassNotFoundException {
        FileInputStream fileInputStream
                = new FileInputStream(subDir+"/" + num);
        ObjectInputStream objectInputStream
                = new ObjectInputStream(fileInputStream);
        TrainGame ret = (TrainGame) objectInputStream.readObject();
        objectInputStream.close();
        return ret;
    }

    public void save(int num, final ResultGame resultGame) throws IOException {
        this.value = resultGame.reward;
        FileOutputStream fileOutputStream
                = new FileOutputStream("train/" + num);
        ObjectOutputStream objectOutputStream
                = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(this);
        objectOutputStream.flush();
        objectOutputStream.close();
    }

    public void add(final Lc0OneStepRecord lc0OneStepRecord) {
        this.lc0OneStepRecordList.add(lc0OneStepRecord);
    }

    public void clear() {
        this.lc0OneStepRecordList.clear();
        value = null;
    }

}
