package com.aquila.chess;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@ToString
@Builder
@Slf4j
public class MCTSStrategyConfig {

    public static MCTSStrategyConfig DEFAULT_WHITE_INSTANCE;
    public static MCTSStrategyConfig DEFAULT_BLACK_INSTANCE;
    private static int DEFAULT_THREAD;

    boolean dirichlet = true;

    int nbThreads = DEFAULT_THREAD;

    int nbStep = 800;

    long millisPerStep = -1;

    int sizeBatch = 50;

    int seed = 1;

    static {
        DEFAULT_THREAD = Runtime.getRuntime().availableProcessors() - 4;
        if (DEFAULT_THREAD < 1) DEFAULT_THREAD = 1;
        log.warn("USED PROCESSORS FOR MCTS SEARCH: {}", DEFAULT_THREAD);
        reset();
    }

    public static void reset() {
        DEFAULT_WHITE_INSTANCE = MCTSStrategyConfig.builder().build();
        DEFAULT_BLACK_INSTANCE = MCTSStrategyConfig.builder().build();
    }

    public static boolean isDirichlet(Alliance pieceAllegiance) {
        switch (pieceAllegiance) {
            case WHITE:
                return DEFAULT_WHITE_INSTANCE.isDirichlet();
            case BLACK:
                return DEFAULT_BLACK_INSTANCE.isDirichlet();
        }
        throw new RuntimeException(String.format("allegiance not know: %s", pieceAllegiance));
    }

    public static boolean isDirichlet(Move move) {
        if (move == null) return DEFAULT_WHITE_INSTANCE.isDirichlet();
        return isDirichlet(move.getAllegiance());
    }

}
