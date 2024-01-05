package com.aquila.chess.fit;

import com.aquila.chess.strategy.mcts.StatisticsFit;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
public class AbstractFit {

    @Getter
    ConfigFit configFit;

    public AbstractFit(String configFile) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(ConfigFit.class);
        File file = new File(configFile);
        this.configFit = (ConfigFit) context.createUnmarshaller().unmarshal(file);
    }

    public void run(TrainFile trainFile, Map<String, StatisticsFit> statistics) {
        Map<Long, File> files = retrieveAllFiles();
        log.info("files:\n{}", files
                .entrySet()
                .stream()
                .map(entry -> String.format("%s", entry.getValue().getPath()))
                .collect(Collectors.joining("\n"))
        );
        files.values().stream().forEach(file -> {
            trainFile.train(file, statistics);
        });
    }

    private Map<Long, File> retrieveAllFiles() {
        final SortedMap<Long, File> files = new TreeMap<>();
        this.configFit.getConfigDirs().forEach(configDir -> {
            File file = new File(configDir.getDirectory());
            FileFilter filters = pathname -> {
                log.info("accept({})", pathname);
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
            if (trainFiles != null) {
                Arrays.stream(trainFiles).forEach(file1 -> {
                    files.put(file1.lastModified(), file1);
                });
            }
        });
        return files;
    }
}
