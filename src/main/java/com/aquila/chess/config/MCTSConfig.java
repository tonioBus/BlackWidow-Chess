package com.aquila.chess.config;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

@Slf4j
public class MCTSConfig {

    static public MCTSConfig mctsConfig = new MCTSConfig();

    private Properties properties = new Properties();

    @Getter
    private int waitInSeconds = 120;

    @Getter
    private double newNodeValue = -0.5;

    @Getter
    private double fpuReduction = 0.0;

    @Getter
    private double dirichletNoise = 0.3;

    @Getter
    private double epsilon = 0.25;

    @Getter
    private MCTSStrategyConfig mctsWhiteStrategyConfig;

    @Getter
    private MCTSStrategyConfig mctsBlackStrategyConfig;

    public static void reload() {
        mctsConfig = new MCTSConfig();
    }

    public MCTSConfig() {
        String dir = "config/mctsConfig.xml";
        try {
            InputStream in = new FileInputStream(dir);
            properties.loadFromXML(in);
            this.waitInSeconds = get("waitInSeconds", Integer.class, waitInSeconds);
            this.newNodeValue = get("newNodeValue", Double.class, newNodeValue);
            this.fpuReduction = get("fpuReduction", Double.class, fpuReduction);
            this.dirichletNoise = get("dirichletNoise", Double.class, dirichletNoise);
            this.epsilon = get("epsilon", Double.class, epsilon);
            mctsWhiteStrategyConfig = new MCTSStrategyConfig("white", properties);
            mctsBlackStrategyConfig = new MCTSStrategyConfig("black", properties);
        } catch (IOException e) {
            log.error("Error when reading properties file using default values", e);
        }
    }

    public boolean isDirichlet(Alliance pieceAllegiance) {
        switch (pieceAllegiance) {
            case WHITE:
                return mctsWhiteStrategyConfig.isDirichlet();
            case BLACK:
                return mctsBlackStrategyConfig.isDirichlet();
        }
        throw new RuntimeException(String.format("allegiance not know: %s", pieceAllegiance));
    }

    public boolean isDirichlet(Move move) {
        if (move == null) return mctsWhiteStrategyConfig.isDirichlet();
        return isDirichlet(move.getAllegiance());
    }

    private <T> T get(String property, Class<T> clazz, T defaultValue) {
        try {
            T value = get(property, clazz);
            if (value == null) {
                log.warn("");
                return defaultValue;
            }
            return value;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            log.warn("Error getting property:{} using default value:{}", property, defaultValue);
            return defaultValue;
        }
    }

    private <T> T get(String property, Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String value = properties.getProperty(property);
        if (value == null) return null;
        Method method = clazz.getDeclaredMethod("valueOf", String.class);
        return clazz.cast(method.invoke(null, value));
    }

}