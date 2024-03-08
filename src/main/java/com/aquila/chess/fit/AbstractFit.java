package com.aquila.chess.fit;

import com.aquila.chess.TrainGame;
import com.aquila.chess.strategy.mcts.StatisticsFit;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Slf4j
public class AbstractFit {

    @Getter
    private final File file;

    @Getter
    ConfigFit configFit;

    SortedMap<Long, File> drawGames = new TreeMap<>();
    SortedMap<Long, File> winGames = new TreeMap<>();
    SortedMap<Long, File> lostGames = new TreeMap<>();

    public AbstractFit(String configFile) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(ConfigFit.class);
        file = new File(configFile);
        this.configFit = (ConfigFit) context.createUnmarshaller().unmarshal(file);
    }

    public void run(TrainFile trainFile, Map<String, StatisticsFit> statistics) {
        retrieveAllFiles();
        log.info("Win   games:{}", winGames.size());
        log.info("Lost  games:{}", lostGames.size());
        log.info("Drawn games:{}", drawGames.size());
        winGames.values().stream().forEach(file -> {
            try {
                trainFile.train(file, statistics);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        lostGames.values().stream().forEach(file -> {
            try {
                trainFile.train(file, statistics);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        drawGames.values().stream().forEach(file -> {
            try {
                trainFile.train(file, statistics);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void retrieveAllFiles() {
        this.configFit.getConfigDirs().forEach(configDir -> {
            File[] trainFiles = getFiles(configDir);
            if (trainFiles != null) {
                Arrays.stream(trainFiles).forEach(file1 -> {
                    try {
                        TrainGame trainGame = TrainGame.load(file1);
                        Double value = trainGame.getValue();
                        if (value == -1.0) {
                            lostGames.put(file1.lastModified(), file1);
                        } else if (value == 0.0) {
                            drawGames.put(file1.lastModified(), file1);
                        } else if (value == 1.0) {
                            winGames.put(file1.lastModified(), file1);
                        } else
                            throw new RuntimeException("Unidentified game value:" + value);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    private static File[] getFiles(ConfigDir configDir) {
        File file = new File(configDir.getDirectory());
        FileFilter filters = pathname -> {
            try {
                String filename = pathname.toPath().getFileName().toString();
                int fileNum = Integer.parseInt(filename);
                return pathname.canRead() &&
                        pathname.isFile() &&
                        fileNum >= configDir.getStartNumber() &&
                        fileNum <= configDir.getEndNumber();
            } catch (NumberFormatException e) {
                return false;
            }
        };
        File[] trainFiles = file.listFiles(filters);
        return trainFiles;
    }
}
