package com.aquila.chess.strategy.mcts;

import com.aquila.chess.Game;
import com.aquila.chess.Helper;
import com.aquila.chess.strategy.FixStrategy;
import com.aquila.chess.strategy.RandomStrategy;
import com.aquila.chess.strategy.StaticStrategy;
import com.aquila.chess.strategy.mcts.inputs.aquila.AquilaInputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNConstants;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static com.aquila.chess.Game.GameStatus.IN_PROGRESS;
import static com.chess.engine.classic.Alliance.BLACK;
import static com.chess.engine.classic.Alliance.WHITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class MCTSSearchAquilaInputsTest {

    AquilaNNTest aquilaNNTest;

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return 2.5;
    };

    @BeforeEach
    public void init() {
        aquilaNNTest = new AquilaNNTest();
        MCTSNode.resetBuildOrder();
    }

    final private AquilaInputsManagerImpl inputsManager = new AquilaInputsManagerImpl();

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 20, 30, 40, 100})
    void testSimpleSearch(int nbStep) throws Exception {
        int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(aquilaNNTest)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(10)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbSearchCalls(nbStep);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed + 1000);
        game.setup(whiteStrategy, blackStrategy);
        assertEquals(Game.GameStatus.IN_PROGRESS, game.play());
        log.info(game.toString());
        final Move move = game.getLastMove();
        final MCTSNode node = whiteStrategy.getDirectRoot();
        log.info("parent:{}", node);
        log.info("CacheSize: {} STATS: {}", deepLearningWhite.getCacheSize(), whiteStrategy.getStatistic());
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(true, 50));
        double policy = node.getChildNodes().get(move).getPolicy();
        log.info("cacheValues:\n{}", deepLearningWhite.getCacheValues());
        log.info("policies[{}]={}", move, policy);
        assertTrue(policy > 0);
        Helper.checkMCTSTree(whiteStrategy);
    }


    @Test
    void testInitSearch() {
        int seed = 10;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(aquilaNNTest)
                .inputsManager(inputsManager)
                .batchSize(50)
                .train(false)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
               // .withNbThread(1)
                .withNbSearchCalls(500);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed);
        game.setup(whiteStrategy, blackStrategy);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            final MCTSGame mctsGame = new MCTSGame(game);
//            assertTrue(mctsGame.getStrategyWhite() instanceof FixMCTSTreeStrategy);
//            assertTrue(mctsGame.getStrategyBlack() instanceof FixMCTSTreeStrategy);
//            assertEquals(Alliance.WHITE, mctsGame.getStrategyWhite().getAlliance());
//            assertEquals(Alliance.BLACK, mctsGame.getStrategyBlack().getAlliance());
//            assertEquals(Alliance.WHITE, mctsGame.getPlayer(Alliance.WHITE).getAlliance());
//            assertEquals(Alliance.BLACK, mctsGame.getPlayer(Alliance.BLACK).getAlliance());
        }
        long endTime = System.currentTimeMillis();
        log.info("Delay: {} ms", (endTime - startTime));
    }

    /**
     * @throws Exception
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- R-B --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  --- --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- K-W ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * <p>
     */
    @ParameterizedTest
    @ValueSource(ints = {4, 5, 10, 30, 50, 100})
    @DisplayName("MCTS tree should avoid white chess-mate")
    void testAvoidWhiteChessMate1Move(int nbSearchCalls) throws Exception {
        final Board board = Board.createBoard("kg1", "re8,kg3", WHITE);
        final NNConstants nnConstant = new NNConstants(1);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnConstant)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(10)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                10,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbSearchCalls(nbSearchCalls);
        final RandomStrategy blackStrategy = new RandomStrategy(BLACK, 10);
        game.setup(whiteStrategy, blackStrategy);
        nnConstant.addIndexOffset(2F, "e8-e1", board);
        nnConstant.addIndexOffset(1F, "g1-h1", board);
        Game.GameStatus status = null;
        status = game.play();
        assertEquals(IN_PROGRESS, status);
        List<MCTSNode> winLoss = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
        log.info("[{}}] Wins/loss EndNodes ({}): {}", whiteStrategy.getAlliance(), winLoss.size(), winLoss.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(nbSearchCalls < 200, 50));
        Helper.checkMCTSTree(whiteStrategy);
        assertTrue(winLoss.size() > 0, "We should have some loss nodes detected for white (to avoid chessmate)");
        log.warn("game:{}", game.toPGN());
    }

    /**
     * @formatter:off <pre>
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  R-B --- --- --- --- --- K-B ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- --- ---  3
     * 2  --- --- --- --- K-W --- --- R-B  2
     * 1  --- --- --- --- --- --- --- ---  1
     *    [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     * @formatter:on
     */
    @ParameterizedTest
    @ValueSource(ints = {50, 200, 400, 800})
    @DisplayName("MCTS tree should avoid white chess-mate")
    void testAvoidWhiteChessMateWith2Rooks(int nbSearchCalls) throws Exception {
        final Board board = Board.createBoard("ke2", "ra8,kg8,rh2", WHITE);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final NNConstants nnConstant = new NNConstants(1);
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnConstant)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(10)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                WHITE,
                deepLearningWhite,
                1,
                updateCpuct,
                -1)
                .withNbSearchCalls(nbSearchCalls);
        final StaticStrategy blackStrategy = new StaticStrategy(BLACK, "G2-G3;A8-A1");
        game.setup(whiteStrategy, blackStrategy);
        Game.GameStatus status = game.play();
        assertEquals(IN_PROGRESS, status);
        List<MCTSNode> lossNodes = whiteStrategy.getDirectRoot().search(MCTSNode.State.LOOSE);
        log.info("[{}}] Wins/loss EndNodes ({}): {}", whiteStrategy.getAlliance(), lossNodes.size(), lossNodes.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(nbSearchCalls < 200, 5));
        Helper.checkMCTSTree(whiteStrategy);
        assertTrue(lossNodes.size() > 0, "We should have some loss nodes detected for white (to avoid chessmate)");

        log.warn("game:{}", game.toPGN());
    }

    /**
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * 8  --- --- --- --- --- --- --- ---  8
     * 7  --- --- --- --- --- --- --- ---  7
     * 6  --- --- --- --- --- --- --- ---  6
     * 5  --- --- --- --- --- --- --- ---  5
     * 4  --- --- --- --- --- --- --- ---  4
     * 3  --- --- --- --- --- --- K-B ---  3
     * 2  P-B --- --- --- --- --- --- ---  2
     * 1  --- --- --- --- --- --- K-W ---  1
     * [a] [b] [c] [d] [e] [f] [g] [h]
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {30, 50, 100, 800})
    @DisplayName("MCTS tree should avoid white chess-mate with promotion")
    void testAvoidWhiteChessMate1MoveWithPromotion(int nbSearchCalls) throws Exception {
        int seed = (int) System.currentTimeMillis();
        final Board board = Board.createBoard("kg1", "pa2,kg3", Alliance.WHITE);
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        final NNConstants nnConstant = new NNConstants(1);
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(nnConstant)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(4)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
            //    .withNbThread(1)
                .withNbSearchCalls(nbSearchCalls);
        final FixStrategy blackStrategy = new FixStrategy(Alliance.BLACK);
        game.setup(whiteStrategy, blackStrategy);
        nnConstant.addIndexOffset(2F, "a2-a1", board);
        nnConstant.addIndexOffset(1F, "g1-f1", board);
        Game.GameStatus status = game.play();
        Move move = game.getLastMove();
        assertEquals(IN_PROGRESS, status);
        List<MCTSNode> winLoss = whiteStrategy.getDirectRoot().search(MCTSNode.State.WIN, MCTSNode.State.LOOSE);
        log.info("[{}}] Wins/loss EndNodes ({}): {}", whiteStrategy.getAlliance(), winLoss.size(), winLoss.stream().map(node -> String.format("%s:%s", node.getState(), node.getMove().toString())).collect(Collectors.joining(",")));
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(nbSearchCalls < 200, 50));
        Helper.checkMCTSTree(whiteStrategy);
        assertTrue(winLoss.size() > 0);
        // Kg1, the only way to escape for white
        assertEquals("Kf1", move.toString());
        Helper.checkMCTSTree(whiteStrategy);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 50, 100, 800})
    void testSearchMonoThreads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 1, 100);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 10, 20, 30, 50, 100, 800})
    void testSearch2Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 2, 100);
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch4Threads(int nbStep) throws Exception {
        testSearchThreads(nbStep, 4, 100);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch8Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 8, 100);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch16Threads(int nbMaxSearchCalls) throws Exception {
        testSearchThreads(nbMaxSearchCalls, 16, 100);
    }

    private void testSearchThreads(int nbMaxSearchCalls, int nbThreads, int batchSize) throws Exception {
        int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).inputsManager(inputsManager).build();
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ.builder()
                .nn(aquilaNNTest)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(batchSize)
                .build();
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(nbThreads)
                .withNbSearchCalls(nbMaxSearchCalls);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed + 1);
        game.setup(whiteStrategy, blackStrategy);
        game.play();
        if (log.isInfoEnabled()) log.info(whiteStrategy.mctsTree4log(nbMaxSearchCalls < 100, 50));
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
    @CsvSource({"1, 4, 4", "4,6,2", "1,6,2", "1,12,2", "1,15,1", "1,16,1", "1,20,1", "1,20,2", "1,50,2", "1,800,50", "2,5,6", "2,10,2", "5,10,2", "2,10,5", "2,20,2", "2,20,5", "2,100,20", "2,800,50"})
    void testEndWithBlackPromotionThreads(final String nbThreadsSz, final String nbMaxSearchCallsSz, final String batchSizeSz) throws Exception {
        int nbMaxSearchCalls = Integer.parseInt(nbMaxSearchCallsSz);
        int batchSize = Integer.parseInt(batchSizeSz);
        int nbThreads = Integer.parseInt(nbThreadsSz);
        int seed = 1;
        final Board board = Board.createBoard("kh1", "pa3,kg3", Alliance.BLACK);
        final Game game = Game.builder().inputsManager(inputsManager).board(board).build();
        final RandomStrategy whiteStrategy = new RandomStrategy(Alliance.WHITE, seed + 1000);
        final DeepLearningAGZ deepLearningBlack = DeepLearningAGZ.builder()
                .nn(aquilaNNTest)
                .inputsManager(inputsManager)
                .train(false)
                .batchSize(batchSize)
                .build();
        final MCTSStrategy blackStrategy = new MCTSStrategy(
                game,
                Alliance.BLACK,
                deepLearningBlack,
                seed,
                updateCpuct,
                -1)
                .withNbThread(nbThreads)
                .withNbSearchCalls(nbMaxSearchCalls);
        game.setup(whiteStrategy, blackStrategy);
        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, Piece.PieceType.PAWN);
        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, Piece.PieceType.PAWN);
        aquilaNNTest.addIndexOffset(0.5, index1, index2);
        game.play();
        log.info("parent:{}", blackStrategy.getDirectRoot());
        log.warn("visits:{}", blackStrategy.getDirectRoot().getVisits());
        if (log.isInfoEnabled()) log.info(blackStrategy.mctsTree4log(nbMaxSearchCalls < 100, 50));
        log.warn("CacheSize: {} STATS: {}", deepLearningBlack.getCacheSize(), blackStrategy.getStatistic()); //statistic.toString());
        if (nbMaxSearchCalls >= 50) {
            MCTSNode bestNode = blackStrategy.findBestReward(blackStrategy.getDirectRoot(), blackStrategy.getDirectRoot().getChildMoves(), false);
            assertEquals("a2", bestNode.move.toString());
        }
        assertEquals(0, deepLearningBlack.getServiceNN().getBatchJobs2Commit().size());
        log.warn("ROOT nb visits:{}", blackStrategy.getDirectRoot().getVisits());
        Helper.checkMCTSTree(blackStrategy);
    }
}
