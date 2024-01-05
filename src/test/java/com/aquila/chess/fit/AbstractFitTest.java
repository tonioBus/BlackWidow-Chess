package com.aquila.chess.fit;

import com.aquila.chess.strategy.mcts.StatisticsFit;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@Slf4j
class AbstractFitTest {

    @Test
    void testAbstractFit() throws JAXBException {
        AbstractFit abstractFit = new AbstractFit("test/configFit.xml");
        TrainFile trainFile = (file,statisticsFitMap) -> log.info("train file:{}", file);
        final Map<String, StatisticsFit> statistics = new HashMap<>();
        abstractFit.run(trainFile, statistics);
    }
}