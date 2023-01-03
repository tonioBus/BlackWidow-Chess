package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.Helper;
import com.aquila.chess.MCTSStrategyConfig;
import com.aquila.chess.strategy.FixMCTSTreeStrategy;
import com.aquila.chess.strategy.FixStrategy;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class MCTSSearchTest {

    NNTest nn;

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return 0.5;
    };

    @BeforeEach
    public void init() {
        nn = new NNTest();
        MCTSNode.resetBuildOrder();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 100})
    void testSearch1Step(int batchSize) throws Exception {
        int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, false, batchSize);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(1);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed + 1000);
        game.setup(whiteStrategy, blackStrategy);
        assertEquals(Game.GameStatus.IN_PROGRESS, game.play());
        final Move move = game.getLastMove();
        final MCTSNode node = whiteStrategy.getCurrentRoot();
        log.info("parent:{}", node);
        log.info("CacheSize: {} STATS: {}", deepLearningWhite.getCacheSize(), whiteStrategy.getStatistic());
        log.info(
                "##########################################################################################################");
        log.info("graph: {}", DotGenerator.toString(node, 15, true));
        double policy = node.getCacheValue().policies[PolicyUtils.indexFromMove(move)];
        log.info("policies[{}]={}", move, policy);
        assertTrue(policy > 0);
        Helper.checkMCTSTree(whiteStrategy);
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @Test
    void testSearchFinal() throws Exception {
        int seed = (int) System.currentTimeMillis();
        final Board board = Board.createBoard("kh1", "pa2,kg3", Alliance.WHITE);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, false, 1);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(50);
        final FixStrategy blackStrategy = new FixStrategy(Alliance.BLACK);
        game.setup(whiteStrategy, blackStrategy);
        game.play();
        Move move = game.getLastMove();
        log.info("white move:{}", move);
        log.info("parent:{}", whiteStrategy.getCurrentRoot());
        final MCTSNode node = whiteStrategy.getCurrentRoot();
        log.info(
                "##########################################################################################################");
        log.warn("graph: {}", DotGenerator.toString(node, 15, true));
        List<MCTSNode> nodes = node.search(MCTSNode.State.LOOSE);
        assertTrue(nodes.size() > 0);
        // Kg1, the only way to escape for white
        assertEquals("Kg1", move.toString());
        log.info("\n{}\n", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 5, true));
        Helper.checkMCTSTree(whiteStrategy);
    }

    @Test
    void testInitSearch() {
        int seed = 10;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, false, 50);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(500);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed);
        game.setup(whiteStrategy, blackStrategy);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            final MCTSGame mctsGame = new MCTSGame(game);
            assertTrue(mctsGame.getStrategyWhite() instanceof FixMCTSTreeStrategy);
            assertTrue(mctsGame.getStrategyBlack() instanceof FixMCTSTreeStrategy);
            assertEquals(Alliance.WHITE, mctsGame.getStrategyWhite().getAlliance());
            assertEquals(Alliance.BLACK, mctsGame.getStrategyBlack().getAlliance());
            assertEquals(Alliance.WHITE, mctsGame.getPlayer(Alliance.WHITE).getAlliance());
            assertEquals(Alliance.BLACK, mctsGame.getPlayer(Alliance.BLACK).getAlliance());
        }
        long endTime = System.currentTimeMillis();
        log.info("Delay: {} ms", (endTime - startTime));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearchMonoThreads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 1, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch2Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 2, 50);
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch4Threads(int nbStep) throws Exception {
        testSearchThreads(nbStep, 4, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch8Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 8, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch16Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 16, 100);
    }

    private void testSearchThreads(int nbMaxSearchCalls, int nbThreads, int batchSize) throws Exception {
        int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, false, batchSize);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(nbThreads)
                .withNbMaxSearchCalls(nbMaxSearchCalls);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed + 1);
        game.setup(whiteStrategy, blackStrategy);
        game.play();
        log.warn("\n{}", DotGenerator.toString(whiteStrategy.getCurrentRoot(), 30, nbMaxSearchCalls < 100));
        log.warn("CacheSize: {} STATS: {}", deepLearningWhite.getCacheSize(), whiteStrategy.getStatistic());
        log.info("statistic:{}", whiteStrategy.getStatistic());
        Helper.checkMCTSTree(whiteStrategy);
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  p-B --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- --- K-W  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @CsvSource({"4,6,2", "1,6,2", "1,12,2", "1,15,1", "1,16,1", "1,20,1", "1,20,2", "1,50,2", "1,800,50", "2,5,6", "2,10,2", "5,10,2", "2,10,5", "2,20,2", "2,20,5", "2,100,20", "2,800,50"})
    void testEndWithBlackPromotionThreads(final String nbThreadsSz, final String nbMaxSearchCallsSz, final String batchSizeSz) throws Exception {
        int nbMaxSearchCalls = Integer.parseInt(nbMaxSearchCallsSz);
        int batchSize = Integer.parseInt(batchSizeSz);
        int nbThreads = Integer.parseInt(nbThreadsSz);
        int seed = 1;
        final Board board = Board.createBoard("kh1", "pa3,kg3", Alliance.BLACK);
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningBlack = new DeepLearningAGZ(nn, false, batchSize);
        final RandomStrategy whiteStrategy = new RandomStrategy(Alliance.WHITE, seed + 1000);
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                Alliance.BLACK,
                deepLearningBlack,
                seed,
                updateCpuct,
                -1)
                .withNbThread(nbThreads)
                .withNbMaxSearchCalls(nbMaxSearchCalls);
        game.setup(whiteStrategy, blackStrategy);
        Piece pawn = board.getPiece(BoardUtils.INSTANCE.getCoordinateAtPosition("a3"));
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
        nn.addIndexOffset(0.5, index1, index2);
        game.play();
        log.info("parent:{}", blackStrategy.getCurrentRoot());
        log.warn("visits:{}\n{}", blackStrategy.getCurrentRoot().getVisits(), DotGenerator.toString(blackStrategy.getCurrentRoot(), 30, nbMaxSearchCalls < 100));
        log.warn("CacheSize: {} STATS: {}", deepLearningBlack.getCacheSize(), blackStrategy.getStatistic()); //statistic.toString());
        if (nbMaxSearchCalls >= 50) {
            MCTSNode bestNode = blackStrategy.findBestRewardsWithLogVisits(blackStrategy.getCurrentRoot());
            assertEquals("a2", bestNode.move.toString());
        }
        assertEquals(0, deepLearningBlack.getServiceNN().getBatchJobs2Commit().size());
        log.warn("ROOT nb visits:{}", blackStrategy.getCurrentRoot().getVisits());
        Helper.checkMCTSTree(blackStrategy);
    }
}
