package com.aquila.chess;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;

@Slf4j
@CommandLine.Command(
        name = "ConvertTrainFile",
        description = "ConvertTrainFile POJO to JSON train files"
)
public class ConvertTrainFile implements Runnable {
    @CommandLine.Option(names = {"-fg", "--firstGame"})
    private int firstGame = 0;

    @CommandLine.Option(names = {"-lg", "--lastGame"})
    private int lastGame = 0;

    @CommandLine.Option(names = {"-dir", "--trainDir"})
    private String trainDir = "train";

    @Override
    public void run() {
        for (int num = firstGame; num <= lastGame; num++) {
            try {
                log.info("Converting {}", TrainGame.getFileName(trainDir, num, TrainGame.MarshallingType.POJO));
                TrainGame trainGame = TrainGame.load("train", num, TrainGame.MarshallingType.POJO);
                trainGame.save("train", num, TrainGame.MarshallingType.JSON);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        ;
    }

    /**
     * The learning rate was set to 0.2 and dropped to 0.02, 0.002,
     * and 0.0002 after 100, 300, and 500 thousand steps for chess
     */
    public static void main(final String[] args) throws Exception {
        CommandLine cmd = new CommandLine(new ConvertTrainFile());
        cmd.execute(args);
    }
}
