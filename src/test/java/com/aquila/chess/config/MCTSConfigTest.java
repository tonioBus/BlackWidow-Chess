package com.aquila.chess.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class MCTSConfigTest {

    @Test
    void testLoadConfig() {
        log.info("Config WHITE:\n{}", MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig());
        log.info("Config BLACK:\n{}", MCTSConfig.mctsConfig.getMctsBlackStrategyConfig());
        assertTrue(MCTSConfig.mctsConfig.getMctsWhiteStrategyConfig().getBatch() > 0);
    }
}