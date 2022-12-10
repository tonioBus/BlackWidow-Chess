package com.aquilla.chess.strategy.mcts;

import com.aquilla.chess.Game;
import com.chess.engine.classic.Alliance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

@Slf4j
public class MCTSSearchMultiThread implements IMCTSSearch {

    private static List<MCTSNode> toPropagates = new ArrayList<>();
    private final long timeMillisPerStep;
    private final long nbMaxSearchCalls;

    ExecutorService WORKER_THREAD_POOL = Executors.newFixedThreadPool(128);

    public ExecutorCompletionService<Integer> executorService = new ExecutorCompletionService<>(WORKER_THREAD_POOL);
    private final StopMode stopMode;

    private final MCTSNode currentRoot;
    private final DeepLearningAGZ deepLearning;
    private final Game gameOriginal;
    private final Alliance color;
    private final UpdateCpuct updateCpuct;
    private final Dirichlet updateDirichlet;
    private final Random rand;
    private final int buildOrder;
    private final Statistic statistic;
    private final int nbThreads;

    /**
     * @param deepLearning
     * @param currentRoot
     * @param color
     * @param updateCpuct
     * @param dirichlet
     * @param rand
     */
    public MCTSSearchMultiThread(
            final int nbThreads,
            final long timeMillisPerStep,
            final long nbMaxSearchCalls,
            final Statistic statistic,
            final DeepLearningAGZ deepLearning,
            final MCTSNode currentRoot,
            final Game gameOriginal,
            final Alliance color,
            final UpdateCpuct updateCpuct,
            final Dirichlet dirichlet,
            final Random rand) {
        this.nbThreads = nbThreads;
        if (timeMillisPerStep < 0 && nbMaxSearchCalls < 0) {
            String msg = String.format("Can not choose the stop mechanism (timing and number of step < 0)");
            log.error(msg);
            throw new RuntimeException(msg);
        }
        if (timeMillisPerStep > 0 && nbMaxSearchCalls > 0) {
            String msg = String.format("Can not choose the stop mechanism (timing and number of step > 0)");
            log.error(msg);
            throw new RuntimeException(msg);
        }
        this.stopMode = (timeMillisPerStep > 0) ? StopMode.TIMING : StopMode.NB_STEP;
        this.timeMillisPerStep = timeMillisPerStep;
        this.nbMaxSearchCalls = nbMaxSearchCalls;
        this.statistic = statistic;
        this.currentRoot = currentRoot;
        this.deepLearning = deepLearning;
        this.gameOriginal = gameOriginal;
        this.color = color;
        this.updateCpuct = updateCpuct;
        this.updateDirichlet = dirichlet;
        this.rand = rand;
        this.buildOrder = currentRoot.getBuildOrder();
    }

    public long search()
            throws InterruptedException {
        long start = System.currentTimeMillis();
        statistic.clear();
        currentRoot.syncSum();
        CacheValues.CacheValue rootValue = currentRoot.getCacheValue();
        if (rootValue != null) {
            rootValue.setNormalized(false);
            log.info("RESET ROOT NORMALIZATION key: {}", currentRoot.getKey());
            MCTSNode.resetBuildOrder();
        }
        int nbSubmit = this.currentRoot.getVisits();
        int nbWorks;
        if (nbMaxSearchCalls < 1) nbWorks = nbThreads;
        else nbWorks = Math.min(nbThreads, (int) nbMaxSearchCalls);
        if (nbWorks < 1) nbWorks = 1;
        for (int i = 0; i < nbWorks; i++) {
            SearchWorker searchWorker = createSearchWalker(i, nbSubmit);
            if (log.isDebugEnabled())
                log.debug("[{}] CREATING TASK:{}", currentRoot.getVisits(), i);
            executorService.submit(searchWorker);
            nbSubmit++;
        }
        boolean isEnding = false;
        while (isEnding == false) {
            Future<Integer> future = executorService.take();
            if (future == null) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) WORKER_THREAD_POOL;
                if (log.isDebugEnabled())
                    log.debug("FUTURE is NULL. NB_THREAD:{} ", threadPoolExecutor.getActiveCount());
                continue;
            }
            try {
                Integer nbSearchWalker = future.get();
                if (log.isDebugEnabled())
                    log.debug("[{}] IS DONE {}:{}", currentRoot.getVisits(), nbSearchWalker.intValue(), future.isDone());
                if (isEnding == false) {
                    boolean isContinue = false;
                    switch (this.stopMode) {
                        case TIMING:
                            isContinue = (System.currentTimeMillis() - start) < timeMillisPerStep;
                            break;
                        case NB_STEP:
                            isContinue = nbSubmit < nbMaxSearchCalls;
                            break;
                    }
                    if (isContinue) {
                        SearchWorker searchWorker = createSearchWalker(
                                nbSearchWalker.intValue(),
                                nbSubmit);
                        if (log.isDebugEnabled())
                            log.debug("[{}] CREATING new TASK:{}", currentRoot.getVisits(), nbSearchWalker.intValue());
                        executorService.submit(searchWorker);
                        nbSubmit++;
                    } else {
                        WORKER_THREAD_POOL.shutdown();
                        while (!WORKER_THREAD_POOL.awaitTermination(200, TimeUnit.MILLISECONDS)) ;
                        isEnding = true;
                        if (log.isDebugEnabled()) log.debug("[{}] END OF SEARCH DETECTED", currentRoot.getVisits());
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            deepLearning.flushJob(true, statistic);
        } catch (ExecutionException e) {
            log.error("Error during last flushJobs", e);
        }
        return currentRoot.getVisits();
    }

    enum StopMode {
        TIMING, NB_STEP
    }

    private SearchWorker createSearchWalker(final int numThread, final int nbNumberSearchCalls) {
        gameOriginal.isInitialPosition();
        final SearchWorker searchWorker = new SearchWorker(
                numThread,
                nbNumberSearchCalls,
                statistic,
                deepLearning,
                currentRoot,
                gameOriginal,
                color,
                updateCpuct,
                updateDirichlet,
                rand);
        gameOriginal.isInitialPosition();
        return searchWorker;
    }
}
