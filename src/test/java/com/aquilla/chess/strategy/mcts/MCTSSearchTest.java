package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.aquilla.chess.strategy.FixStrategy;
import com.aquilla.chess.strategy.RandomStrategy;
import com.aquilla.chess.utils.DotGenerator;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.Move;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class MCTSSearchTest {

    private static final Dirichlet dirichlet = game -> false;

    NNTest nn;

    final UpdateCpuct updateCpuct = (nbStep) -> {
        return 0.5;
    };

    private final Dirichlet updateDirichlet = (nbStep) -> {
        return false;
    };

    @BeforeEach
    public void init() {
        nn = new NNTest();
        MCTSNode.resetBuildOrder();
    }

    @Test
    void testSearch() throws InterruptedException {
        int seed = 1;
        final Board board = Board.createStandardBoard();
        final Game game = Game.builder().board(board).build();
        final DeepLearningAGZ deepLearningWhite = new DeepLearningAGZ(nn, false, 5);
        final MCTSStrategy whiteStrategy = new MCTSStrategy(
                game,
                Alliance.WHITE,
                deepLearningWhite,
                seed,
                updateCpuct,
                -1)
                .withNbThread(1)
                .withNbMaxSearchCalls(10);
        final RandomStrategy blackStrategy = new RandomStrategy(Alliance.BLACK, seed + 1000);
        game.setup(whiteStrategy, blackStrategy);
        final MCTSGame mctsGame = new MCTSGame(game);
        whiteStrategy.setCurrentRootNode(game, null);
        final Statistic statistic = new Statistic();
        final MCTSSearchMultiThread mctsSearchMultiThread = new MCTSSearchMultiThread(
                1,
                1,
                -1,
                10,
                statistic,
                deepLearningWhite,
                whiteStrategy.getRoot(),
                mctsGame,
                Alliance.WHITE,
                updateCpuct,
                updateDirichlet,
                new Random());
        mctsSearchMultiThread.search();
        log.info("parent:{}", whiteStrategy.getRoot());
        final MCTSNode node = whiteStrategy.getRoot();
        log.warn("CacheSize: {} STATS: {}", deepLearningWhite.getCacheSize(), statistic.toString());
        log.warn(
                "##########################################################################################################");
        log.warn("graph: {}", DotGenerator.toString(node, 15));
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
        int seed = (int)System.currentTimeMillis();
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
                .withNbMaxSearchCalls(500);
        final FixStrategy blackStrategy = new FixStrategy(Alliance.BLACK);
        game.setup(whiteStrategy, blackStrategy);
        final MCTSGame mctsGame = new MCTSGame(game);
        whiteStrategy.setCurrentRootNode(game, null);
        final Random rand = new Random(2);
        final Statistic statistic = new Statistic();
        final MCTSSearchMultiThread mctsSearchMultiThread = new MCTSSearchMultiThread(
                1,
                1,
                -1,
                500,
                statistic,
                deepLearningWhite,
                whiteStrategy.getRoot(),
                mctsGame,
                Alliance.WHITE,
                updateCpuct,
                updateDirichlet,
                rand);
        mctsSearchMultiThread.search();
        log.info("parent:{}", whiteStrategy.getRoot());
        final MCTSNode node = whiteStrategy.getRoot();
        log.info(
                "##########################################################################################################");
        log.warn("graph: {}", DotGenerator.toString(node, 15, true));
        List<MCTSNode> nodes = node.search(MCTSNode.State.LOOSE);
        assertEquals(1, nodes.size());
        game.play();
        Move move = game.getLastMove();
        log.info("white move:{}", move);
        // Kg1, the only way to escape for white
        assertEquals("Kg1", move.toString());
    }

//    @Test
//    void testInitSearch() throws ChessPositionException {
//        final DeepLearningAGZ deepLearning = new DeepLearningAGZ(nn, false, 50);
//        MCTSPlayer playerWhite = new MCTSPlayer(deepLearning, 1, updateCpuct, 200);
//        RandomPlayer playerBlack = new RandomPlayer(0);
//        final Game game = new Game(//
//                new Board(), //
//                playerWhite, //
//                playerBlack);
//        game.initWithAllPieces();
//        playerWhite.initCurrentRootNode();
//        long startTime = System.currentTimeMillis();
//        for (int i = 0; i < 1000; i++) {
//            game.copy(playerWhite, playerBlack);
//        }
//        long endTime = System.currentTimeMillis();
//        log.info("Delay: {} ms", (endTime - startTime));
//    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearchMonoThreads(int nbStep) throws InterruptedException {
        testSearchThreads(nbStep, 1, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch2Threads(int nbStep) throws InterruptedException {
        testSearchThreads(nbStep, 2, 10);
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch4Threads(int nbStep) throws InterruptedException {
        testSearchThreads(nbStep, 4, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch8Threads(int nbStep) throws InterruptedException {
        testSearchThreads(nbStep, 8, 10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 50, 100, 800})
    void testSearch16Threads(int nbStep) throws InterruptedException {
        testSearchThreads(nbStep, 16, 100);
    }

    private void testSearchThreads(int nbStep, int nbThread, int batchSize) throws InterruptedException {
//        Random rand = new Random(1);
//        final DeepLearningAGZ deepLearning = new DeepLearningAGZ(nn, false, batchSize);
//        MCTSPlayer playerWhite = new MCTSPlayer(deepLearning, 1, updateCpuct, -1).withNbMaxSearchCalls(3);
//        RandomPlayer playerBlack = new RandomPlayer(2);
//        final Game game = new Game(//
//                new Board(), //
//                playerWhite, //
//                playerBlack);
//        game.initWithAllPieces();
//        playerWhite.initCurrentRootNode();
//        Statistic statistic = new Statistic();
//        final MCTSSearchMultiThread mctsSearch = new MCTSSearchMultiThread(
//                nbThread,
//                -1,
//                nbStep,
//                statistic,
//                deepLearning,
//                playerWhite.getRoot(),
//                game,
//                Color.WHITE,
//                updateCpuct,
//                updateDirichlet,
//                rand);
//        mctsSearch.search();
//        log.warn("\n{}", DotGenerator.toString(playerWhite.getRoot(), 30, nbStep < 100));
//        log.warn("CacheSize: {} STATS: {}", deepLearning.getCacheSize(), statistic.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8})
    void testGameCopyMultiThread(int nbThreads) throws InterruptedException {
//        ExecutorService executorService = Executors.newFixedThreadPool(nbThreads * 2);
//        final Random rand = new Random();
//        ChessPlayer playerWhite = new RandomPlayer(rand.nextInt(1000));
//        ChessPlayer playerBlack = new RandomPlayer(rand.nextInt(1000));
//        final Game game = new Game(//
//                new Board(), //
//                playerWhite, //
//                playerBlack);
//        game.initWithAllPieces();
//        final AtomicInteger nbEnds = new AtomicInteger();
//        final Callable<Void> callable = () -> {
//            log.warn("start callable");
//            ChessPlayer playerWhite1 = new RandomPlayer(rand.nextInt(1000));
//            ChessPlayer playerBlack1 = new RandomPlayer(rand.nextInt(1000));
//            Game gameCopy = game.copy(playerWhite1, playerBlack1);
//            try {
//                do {
//                    for (int j = 0; j < 400; j++) {
//                        gameCopy.getPlayer(gameCopy.getColorToPlay()).getPossibleLegalMoves();
//                    }
//                    gameCopy.play();
//                } while (true);
//            } catch (final EndOfGameException e) {
//                log.warn("end callable:{}", gameCopy);
//            }
//            nbEnds.incrementAndGet();
//            return null;
//        };
//        for (int i = 0; i < nbThreads; i++) {
//            executorService.submit(callable);
//        }
//        executorService.shutdown();
//        while (!executorService.awaitTermination(50, TimeUnit.SECONDS)) ;
//        assertEquals(nbThreads, nbEnds.get());
    }

    /**
     * @throws ChessPositionException
     * @throws EndOfGameException
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
    void testEndWithBlackPromotionThreads(final String nbThreadsSz, final String nbMaxSearchCallsSz, final String batchSizeSz) throws InterruptedException {
//        int nbMaxSearchCalls = Integer.parseInt(nbMaxSearchCallsSz);
//        int batchSize = Integer.parseInt(batchSizeSz);
//        int nbThreads = Integer.parseInt(nbThreadsSz);
//        Random rand = new Random(0);
//        final DeepLearningAGZ deepLearning = new DeepLearningAGZ(nn, false, batchSize);
//        final Board board = new Board();
//        final ChessPlayer whitePlayer = new RandomPlayer(0);
//        final MCTSPlayer blackPlayer = new MCTSPlayer(deepLearning, 1, updateCpuct, -1) //
//                .withNbMaxSearchCalls(20).withDirichlet(dirichlet);
//        final Game game = new Game(board, whitePlayer, blackPlayer);
//
//        whitePlayer.addPieces("KH1");
//        blackPlayer.addPieces("PA3,KG3");
//        game.init();
//        blackPlayer.initCurrentRootNode();
//        game.setColorToPlay(Color.BLACK);
//        Piece pawn = board.getPiece("A3");
//        int index1 = PolicyUtils.indexFromMove(0, 2, 0, 1, pawn);
//        int index2 = PolicyUtils.indexFromMove(0, 1, 0, 0, pawn);
//        nn.addIndexOffset(0.5, index1, index2);
//        Statistic statistic = new Statistic();
//        final MCTSSearchMultiThread mctsSearchMultiThread = new MCTSSearchMultiThread(
//                nbThreads,
//                -1,
//                nbMaxSearchCalls,
//                statistic,
//                deepLearning,
//                blackPlayer.getRoot(),
//                game,
//                Color.BLACK,
//                updateCpuct,
//                updateDirichlet,
//                rand);
//        mctsSearchMultiThread.search();
//        log.warn("\n{}", DotGenerator.toString(blackPlayer.getRoot(), 30, nbMaxSearchCalls < 100));
//        log.warn("CacheSize: {} STATS: {}", deepLearning.getCacheSize(), statistic.toString());
//        if (nbMaxSearchCalls >= 50) {
//            MCTSNode bestNode = blackPlayer.findBestRewardsWithLogVisits(blackPlayer.getRoot());
//            assertEquals("a3-a2", bestNode.getBasicMove().toString());
//        }
//        deepLearning.getServiceNN().getTmpCacheValues().forEach((key, cacheValue) -> {
//            log.warn("NODE NOT PROPAGATED [{}]: sync:{} {} -> {}",
//                    key,
//                    cacheValue.getNode() == null ? "NO-NODE" : cacheValue.getNode().isSync(),
//                    cacheValue.getNode() == null ? "NO-MOVE" : cacheValue.getNode().getBasicMove(),
//                    cacheValue);
//        });
//        assertEquals(0, deepLearning.getServiceNN().getBatchJobs2Commit().size());
//        log.warn("ROOT nb visits:{}", blackPlayer.getRoot().getVisits());
//        Helper.checkMCTSTree(blackPlayer.getRoot());
    }
}
