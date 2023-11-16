package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.HungryStrategy;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
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
    @Disabled
    @Order(0)
    void testCreate(int nbStep) throws Exception {
        final Board board = Board.createStandardBoard();
        Lc0InputsManagerImpl inputsManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        int seed = 1;
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        while (game.play() == Game.GameStatus.IN_PROGRESS && game.getNbStep() < nbStep) ;
        MCTSGame mctsGame = new MCTSGame(game);
        assertEquals(nbStep, game.getMoves().size());
        assertEquals(Integer.min(nbStep, 8), inputsManager.getLc0Last8Inputs().size());
        int len = nbStep < 8 ? 0 : nbStep - 8;
        String gameMoves = game.getMoves().stream().skip(len).map(move -> move.toString()).collect(Collectors.joining(","));
        String mctsGameMoves = inputsManager.getLc0Last8Inputs().stream().map(input -> input.move().toString()).collect(Collectors.joining(","));
        log.info("game {} <-> {} mctsGame", gameMoves, mctsGameMoves);
        assertEquals(gameMoves, mctsGameMoves);
    }

    // @ParameterizedTest
    // @ValueSource(ints = {6})
    // @Order(1)
    void testPlayDependencies(int nbStep) throws Exception {
        assertTrue((nbStep & 1) == 0, "nbStep should be even (white to play at the end of game.play() iteration");
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        int seed = 1;
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        while (game.play() == Game.GameStatus.IN_PROGRESS && game.getNbStep() < nbStep) ;
        log.info("board:\n{}\n", game.toPGN());
        MCTSGame mctsGame = new MCTSGame(game);

        game.play();
        game.play();

        log.info("board:\n{}\n", game.toPGN());
        // log.info("MCTS board:\n{}\n", mctsGame.toPGN());
        assertEquals(8, game.getMoves().size());
        Assertions.assertEquals(2, mctsGame.getMoves().size());

        game.play();
        game.play();

        assertEquals(10, game.getMoves().size());
        Assertions.assertEquals(2, mctsGame.getMoves().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @Disabled
    void testMCTSLc0Hashcode(int seed) throws Exception {
        final Board board = Board.createStandardBoard();
        Lc0InputsManagerImpl inputsManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new HungryStrategy(Alliance.BLACK, board.blackPlayer()));
        Map<Long, MCTSGame> hashcodes = new HashMap<>();
        Game.GameStatus status;
        int nbSameHashcode = 0;
        do {
            status = game.play();
            final MCTSGame mctsGame = new MCTSGame(game);
            assertEquals(Math.min(8, game.getNbStep()), inputsManager.getLc0Last8Inputs().size());
            long hashcode = mctsGame.hashCode(game.getCurrentPLayerColor(), null);
            // next test to be sure that hashcode is stateless
            assertEquals(hashcode, mctsGame.hashCode(game.getCurrentPLayerColor(), null));
            if (hashcodes.containsKey(hashcode)) {
                nbSameHashcode++;
                String currentLastMoves = inputsManager.getLc0Last8Inputs().stream().map(input -> input.move().toString()).collect(Collectors.joining(","));
                MCTSGame oldMctsGame = hashcodes.get(hashcode);
                Lc0InputsManagerImpl oldInputsManager = (Lc0InputsManagerImpl) oldMctsGame.getInputsManager();
                String oldLastMoves = oldInputsManager.getLc0Last8Inputs().stream().map(input -> input.move().toString()).collect(Collectors.joining(","));
                if (!currentLastMoves.equals(oldLastMoves)) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(String.format("SAME HASHCODE FOR 2 DIFFERENT BOARD:%s\n", hashcode));
                    sb.append("OLD BOARD:\n");
                    sb.append(hashcodes.get(hashcode));
                    sb.append("\nNEW BOARD;\n");
                    sb.append(inputsManager.getHashCodeString(game.getLastBoard(), null, game.getMoves(), game.getCurrentPLayerColor()));
                    assertNotEquals(hashcode, hashcode, sb.toString());
                }
            }
            hashcodes.put(hashcode, mctsGame); //.getHashCodeString(mctsGame.getColor2play(), null));
        } while (status == Game.GameStatus.IN_PROGRESS);
        assertTrue(game.getMoves().size() > 3);
        log.info("NBSTEP:{} STATUS:{} GAME:\n{}", game.getMoves().size(), status, game);
        log.info("nbSameHashcode:{}", nbSameHashcode);
        // arbitrary: 10
        assertTrue(nbSameHashcode < 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void testMCTSAquilaHashcode(int seed) throws Exception {
        final Board board = Board.createStandardBoard();
        InputsManager inputsManager = new AquilaInputsManagerImpl();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new HungryStrategy(Alliance.BLACK, board.blackPlayer()));
        Map<Long, MCTSGame> hashcodes = new HashMap<>();
        Game.GameStatus status;
        int nbSameHashcode = 0;
        do {
            status = game.play();
            final MCTSGame mctsGame = new MCTSGame(game);
            long hashcode = mctsGame.hashCode(game.getCurrentPLayerColor(), null);
            // next test to be sure that hashcode is stateless
            assertEquals(hashcode, mctsGame.hashCode(game.getCurrentPLayerColor(), null));
            if (hashcodes.containsKey(hashcode)) {
                nbSameHashcode++;
                MCTSGame oldMctsGame = hashcodes.get(hashcode);
                AquilaInputsManagerImpl oldInputsManager = (AquilaInputsManagerImpl) oldMctsGame.getInputsManager();
                StringBuffer sb = new StringBuffer();
                sb.append(String.format("SAME HASHCODE FOR 2 DIFFERENT BOARD:%s\n", hashcode));
                sb.append("OLD BOARD:\n");
                sb.append(oldInputsManager.getHashCodeString(oldMctsGame.getLastBoard(), null, game.getMoves(), game.getCurrentPLayerColor()));
                sb.append("\nNEW BOARD;\n");
                sb.append(inputsManager.getHashCodeString(game.getLastBoard(), null, game.getMoves(), game.getCurrentPLayerColor()));
                // assertNotEquals(hashcode, hashcode, sb.toString());
                log.info(sb.toString());

            }
            hashcodes.put(hashcode, mctsGame); //.getHashCodeString(mctsGame.getColor2play(), null));
        } while (status == Game.GameStatus.IN_PROGRESS);
        assertTrue(game.getMoves().size() > 3);
        log.info("NBSTEP:{} STATUS:{} GAME:\n{}", game.getMoves().size(), status, game);
        log.info("nbSameHashcode:{}", nbSameHashcode);
        // arbitrary: 10
        //assertTrue(nbSameHashcode < 10);
    }

}