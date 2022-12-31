package com.aquila.chess.strategy.mcts;

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
    private final MCTSGame gameRoot;
    private final Alliance color;
    private final UpdateCpuct updateCpuct;
    private final Dirichlet updateDirichlet;
    private final Random rand;
    private final int buildOrder;
    private final Statistic statistic;
    private final int nbThreads;
    private int nbStep;

    /**
     * @param deepLearning
     * @param currentRoot
     * @param color
     * @param updateCpuct
     * @param dirichlet
     * @param rand
     */
    public MCTSSearchMultiThread(
            final int nbStep,
            final int nbThreads,
            final long timeMillisPerStep,
            final long nbMaxSearchCalls,
            final Statistic statistic,
            final DeepLearningAGZ deepLearning,
            final MCTSNode currentRoot,
            final MCTSGame gameRoot,
            final Alliance color,
            final UpdateCpuct updateCpuct,
            final Dirichlet dirichlet,
            final Random rand) {
        this.nbStep = nbStep;
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
        this.gameRoot = gameRoot;
        this.color = color;
        this.updateCpuct = updateCpuct;
        this.updateDirichlet = dirichlet;
        this.rand = rand;
        this.buildOrder = currentRoot == null ? 0 : currentRoot.getBuildOrder();
    }

    /**
     * @return number of visits
     * @throws InterruptedException
     */
    public long search()
            throws InterruptedException {
        long start = System.currentTimeMillis();
        statistic.clear();
        currentRoot.syncSum();
        CacheValues.CacheValue rootValue = currentRoot.getCacheValue();
        if (rootValue != null) {
            rootValue.setNormalized(false);
            log.info("[{}] RESET ROOT NORMALIZATION key: {}", this.nbStep, currentRoot.getKey());
            MCTSNode.resetBuildOrder();
        }
        int nbSubmit = this.currentRoot.getVisits();
        int nbWorks;
        if (nbMaxSearchCalls < 1) nbWorks = nbThreads;
        else nbWorks = Math.min(nbThreads, (int) nbMaxSearchCalls);
        if (nbWorks < 1) nbWorks = 1;
        for (int i = 0; i < nbWorks; i++) {
            MCTSSearchWalker MCTSSearchWalker = createSearchWalker(nbStep, i, nbSubmit);
            if (log.isDebugEnabled())
                log.debug("[{}] CREATING TASK:{} childs:{}", nbStep, i, currentRoot.getChilds().size());
            executorService.submit(MCTSSearchWalker);
            nbSubmit++;
        }
        boolean isEnding = false;
        while (isEnding == false) {
            Future<Integer> future = executorService.take();
            if (future == null) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) WORKER_THREAD_POOL;
                if (log.isErrorEnabled())
                    log.error("FUTURE is NULL. NB_THREAD:{} ", threadPoolExecutor.getActiveCount());
                throw new Error("future null !!!");
                // continue;
            }
            try {
                Integer nbSearchWalker = future.get();
                if (log.isDebugEnabled())
                    log.debug("[{}] IS DONE {}:{} childs:{}", nbStep, nbSearchWalker.intValue(), future.isDone(), this.currentRoot.getChilds().size());
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
                        MCTSSearchWalker MCTSSearchWalker = createSearchWalker(
                                nbStep,
                                nbSearchWalker.intValue(),
                                nbSubmit);
                        if (log.isDebugEnabled())
                            log.debug("[{}] CREATING new TASK:{} childs:{}", nbStep, nbSearchWalker.intValue(), this.currentRoot.getChilds().size());
                        executorService.submit(MCTSSearchWalker);
                        nbSubmit++;
                    } else {
                        WORKER_THREAD_POOL.shutdown();
                        while (!WORKER_THREAD_POOL.awaitTermination(200, TimeUnit.MILLISECONDS)) ;
                        isEnding = true;
                        if (log.isInfoEnabled())
                            log.info("[{}] END OF SEARCH DETECTED childs:{}", nbStep, currentRoot.getChilds().size());
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

    private MCTSSearchWalker createSearchWalker(final int nbStep, final int numThread, final int nbSubmit) {
        // gameOriginal.isInitialPosition();
        final MCTSSearchWalker MCTSSearchWalker = new MCTSSearchWalker(
                nbStep,
                numThread,
                nbSubmit,
                statistic,
                deepLearning,
                currentRoot,
                gameRoot,
                color,
                updateCpuct,
                updateDirichlet,
                rand);
        // gameOriginal.isInitialPosition();
        return MCTSSearchWalker;
    }
}
