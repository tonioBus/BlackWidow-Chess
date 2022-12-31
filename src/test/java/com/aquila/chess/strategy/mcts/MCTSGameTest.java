package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.strategy.HungryStrategy;
import com.aquila.chess.strategy.RandomStrategy;
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
    void testCreate(int nbStep) throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        int seed = 1;
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        while (game.play() == Game.GameStatus.IN_PROGRESS && game.getNbStep() < nbStep) ;
        MCTSGame mctsGame = new MCTSGame(game);
        assertEquals(nbStep, game.getMoves().size());
        assertEquals(nbStep, game.getTransitions().size());
        assertEquals(Integer.min(nbStep, 8), mctsGame.getLastMoves().size());
        int len = nbStep < 8 ? 0 : nbStep - 8;
        String gameMoves = game.getMoves().stream().skip(len).map(move -> move.toString()).collect(Collectors.joining(","));
        String mctsGameMoves = mctsGame.getLastMoves().stream().map(move -> move.toString()).collect(Collectors.joining(","));
        log.info("game {} <-> {} mctsGame", gameMoves, mctsGameMoves);
        assertEquals(gameMoves, mctsGameMoves);
    }

    @ParameterizedTest
    @ValueSource(ints = {6})
    @Order(1)
    void testPlayDependencies(int nbStep) throws Exception {
        assertTrue((nbStep & 1) == 0, "nbStep should be even (white to play at the end of game.play() iteration");
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        int seed = 1;
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        while (game.play() == Game.GameStatus.IN_PROGRESS && game.getNbStep() < nbStep) ;
        log.info("board:\n{}\n", game.toPGN());
        MCTSGame mctsGame = new MCTSGame(game);
        mctsGame.nextMoves("c1-f4", "g8-f6");
        mctsGame.play();
        mctsGame.play();

        game.play();
        game.play();

        log.info("board:\n{}\n", game.toPGN());
        log.info("MCTS board:\n{}\n", mctsGame.toPGN());
        assertEquals(8, game.getMoves().size());
        Assertions.assertEquals(2, mctsGame.getMoves().size());

        game.play();
        game.play();

        assertEquals(10, game.getMoves().size());
        Assertions.assertEquals(2, mctsGame.getMoves().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void testMCTSHashcode(int seed) throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new HungryStrategy(Alliance.BLACK, board.blackPlayer()));
        Map<Long, MCTSGame> hashcodes = new HashMap<>();
        Game.GameStatus status;
        int nbSameHashcode = 0;
        do {
            status = game.play();
            final MCTSGame mctsGame = new MCTSGame(game);
            assertEquals(Math.min(8, game.getNbStep()), mctsGame.getLastInputs().size());
            long hashcode = mctsGame.hashCode(mctsGame.getColor2play(), null);
            // next test to be sure that hashcode is stateless
            assertEquals(hashcode, mctsGame.hashCode(mctsGame.getColor2play(), null));
            if (hashcodes.containsKey(hashcode)) {
                nbSameHashcode++;
                String currentLastMoves = mctsGame.getLastMoves().stream().map(move -> move.toString()).collect(Collectors.joining(","));
                MCTSGame oldMctsGame = hashcodes.get(hashcode);
                String oldLastMoves = oldMctsGame.getLastMoves().stream().map(move -> move.toString()).collect(Collectors.joining(","));
                if (!currentLastMoves.equals(oldLastMoves)) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(String.format("SAME HASHCODE FOR 2 DIFFERENT BOARD:%s\n", hashcode));
                    sb.append("OLD BOARD:\n");
                    sb.append(hashcodes.get(hashcode));
                    sb.append("\nNEW BOARD;\n");
                    sb.append(mctsGame.getHashCodeString(mctsGame.getColor2play(), null));
                    assertNotEquals(hashcode, hashcode, sb.toString());
                }
            }
            hashcodes.put(hashcode, mctsGame); //.getHashCodeString(mctsGame.getColor2play(), null));
        } while (status == Game.GameStatus.IN_PROGRESS);
        assertTrue(game.getTransitions().size() > 3);
        log.info("NBSTEP:{} STATUS:{} GAME:\n{}", game.getTransitions().size(), status, game);
        log.info("nbSameHashcode:{}", nbSameHashcode);
        // arbitrary: 10
        assertTrue(nbSameHashcode < 10);
    }

}