package com.aquila.chess;

import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.mcts.MCTSGame;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.List;
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

    @Test
    public void testPoliciesUtils() {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        List<Move> moves = game.getPlayer(Alliance.WHITE).getLegalMoves();
        for (Move move : moves) {
            int policyIndex = PolicyUtils.indexFromMove(move, false);
            String moveSz = PolicyUtils.moveFromIndex(policyIndex, moves, false);
            log.info("WHITE move:{} index:{} moveSz:{}", move, policyIndex, moveSz);
        }
        moves = game.getPlayer(Alliance.BLACK).getLegalMoves();
        for (Move move : moves) {
            int policyIndex = PolicyUtils.indexFromMove(move, false);
            String moveSz = PolicyUtils.moveFromIndex(policyIndex, moves, false);
            log.info("BLACK move:{} index:{} moveSz:{}", move, policyIndex, moveSz);
        }
    }

    @Test
    public void testEntireGamesPoliciesUtils() throws Exception {
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        while (game.getStatus() == Game.GameStatus.IN_PROGRESS) {
            List<Move> moves = game.getNextPlayer().getLegalMoves();
//            List<Move> filteredMoves = moves.stream().filter(move -> index == PolicyUtils.indexFromMove(move)).collect(Collectors.toList());
//            if (filteredMoves.isEmpty()) {
//                log.error("Index : {} not found on possible moves", index);
//                return String.format("Index:%s not found", index);
//            }
//            if (filteredMoves.size() != 1) {
//                log.error("Index : {} get multiple moves: {}", filteredMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
//                return String.format("Index:%s not found in %s", index, filteredMoves.stream().map(move -> move.toString()).collect(Collectors.joining(",")));
//            }
//            return String.format("Move:%s", filteredMoves.get(0).toString());

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
            if (!gameOriginal.isInitialPosition()) throw
                    new RuntimeException("Not initial position");
            for (int i = 0; i < 100; i++) {
                Collection<Move> moves = mctsGame.getLastBoard().currentPlayer().getLegalMoves(Move.MoveStatus.DONE);
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
    @Disabled
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