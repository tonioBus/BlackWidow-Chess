package com.aquila.chess;

import com.aquila.chess.strategy.mcts.ResultGame;
import lombok.Getter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

public class TrainGameFloat implements Serializable {

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
    final LinkedList<OneStepRecordFloat> oneStepRecordList = new LinkedList<>();

    public TrainGameFloat() {
    }

    public static TrainGameFloat load(int num) throws IOException, ClassNotFoundException {
        FileInputStream fileInputStream
                = new FileInputStream("train/" + num);
        ObjectInputStream objectInputStream
                = new ObjectInputStream(fileInputStream);
        TrainGameFloat ret = (TrainGameFloat) objectInputStream.readObject();
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

    public void add(final OneStepRecordFloat oneStepRecord) {
        this.oneStepRecordList.add(oneStepRecord);
    }

    public void clear() {
        this.oneStepRecordList.clear();
        value = null;
    }

}
