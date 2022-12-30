package com.aquilla.chess;

import com.aquilla.chess.strategy.HungryStrategy;
import com.aquilla.chess.strategy.RandomStrategy;
import com.aquilla.chess.strategy.mcts.MCTSGame;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class GameTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    public void testRandomPlayer(int seed) {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        game.setup(new RandomStrategy(Alliance.WHITE, seed), new RandomStrategy(Alliance.BLACK, seed + 1));
        try {
            while (game.play() == Game.GameStatus.IN_PROGRESS) ;
        } catch (Throwable t) {
            log.error("Error during a play status:" + game.getStatus(), t);
            log.info("GAME:\n{}", game);
        } finally {
            log.info("END GAME::\n{}", game);
        }
    }

    class ThreadGetPossibleMove implements Callable<Integer> {

        final String label;
        final Game gameOriginal;

        final int nbGame;

        public ThreadGetPossibleMove(final String label, final int nbGame, final Game gameOriginal) {
            this.label = label;
            this.gameOriginal = gameOriginal;
            this.nbGame = nbGame;
        }

        @Override
        public Integer call() {
            log.info("[{}] BEGIN", label);
            final MCTSGame mctsGame = new MCTSGame(gameOriginal);
            // final Game gameCopy = gameOriginal.copy( Alliance.WHITE, new FixMCTSTreeStrategy(Alliance.WHITE), new FixMCTSTreeStrategy(Alliance.BLACK));
            if (!gameOriginal.isInitialPosition()) throw new RuntimeException("Not initial position");
            for (int i = 0; i < 100; i++) {
                Collection<Move> moves = mctsGame.getNextPlayer().getLegalMoves(Move.MoveStatus.DONE);
                assertEquals(20, moves.size());
                gameOriginal.isInitialPosition();
                if (!gameOriginal.isInitialPosition()) throw new RuntimeException("Not initial position");
            }
            log.info("[{}] END", label);
            return this.nbGame;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 16})
    void testCopyGameAndGetPossibleMoves(int nbThread) throws InterruptedException, ExecutionException {
        final Board board = Board.createStandardBoard();
        final Game gameOriginal = Game.builder().board(board).build();
        gameOriginal.setup(new RandomStrategy(Alliance.WHITE, 1), new RandomStrategy(Alliance.BLACK, 2));
        ExecutorService WORKER_THREAD_POOL = Executors.newFixedThreadPool(128);
        ExecutorCompletionService<Integer> executorService = new ExecutorCompletionService<>(WORKER_THREAD_POOL);
        for (int i = 0; i < nbThread; i++) {
            ThreadGetPossibleMove threadGetPossibleMove = new ThreadGetPossibleMove("Game:" + i, i, gameOriginal);
            Future<Integer> future = executorService.submit(threadGetPossibleMove);
            assertFalse(future.isDone());
        }
        int nb = 0;
        while (nb != nbThread) {
            Future<Integer> future = executorService.take();
            if (future == null) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) WORKER_THREAD_POOL;
                if (log.isDebugEnabled())
                    log.debug("FUTURE is NULL. NB_THREAD:{} ", threadPoolExecutor.getActiveCount());
                continue;
            }
            int i = future.get();
            assertTrue(future.isDone());
            nb++;
            log.info("JOB DONE:{}", i);
        }
        gameOriginal.isInitialPosition();
    }

 }