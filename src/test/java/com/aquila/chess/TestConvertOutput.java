package com.aquila.chess;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
public class TestConvertOutput {

    @ParameterizedTest
    @ValueSource(floats = {0, 0.2F, 0.5F, 0.6F, 1})
    public void testConvert(float value) {
        log.info("convert({}) -> {}", value, value * 2.0 - 1.0);
    }
}
