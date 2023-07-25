package com.aquila.chess.strategy.mcts.inputs.aquila;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.HungryStrategy;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class MCTSGameTest {

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 30})
    @Order(0)
    void testPlay(int nbStep) throws Exception {
        final Board board = Board.createStandardBoard();
        AquilaInputsManagerImpl inputsManager = new AquilaInputsManagerImpl();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        int seed = 1;
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        while (game.play() == Game.GameStatus.IN_PROGRESS && game.getNbStep() < nbStep) ;
        log.info("gameMoves {}", game.getMoves());
    }

}