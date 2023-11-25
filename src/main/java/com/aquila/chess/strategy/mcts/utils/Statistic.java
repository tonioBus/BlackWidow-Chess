package com.aquila.chess.strategy.mcts.utils;

import com.aquila.chess.strategy.mcts.CacheValues;

public class Statistic {
    public int nbCalls;
    public int nbPlay;
    public int nbPossibleMoves;
    public int nbRetrieveNNCachedValues;
    public int nbRetrieveNNValues;
    public int nbRetrieveNNCachedPolicies;
    public int nbRandomSelection;
    public int nbRandomSelectionBestMoves;
    public int maxRandomSelectionBestMoves;
    public int minRandomSelectionBestMoves;
    public int nbGoodSelection;
    public int nbSubmitJobs;
    public int totalWinNodes;
    public int totalLostNodes;
    public int totalDrawnNodes;

    public Statistic() {
        clearEachGame();
    }

    public void clearEachStep() {
        nbCalls = 0;
        nbPlay = 0;
        nbPossibleMoves = 0;
        nbRetrieveNNCachedValues = 0;
        nbRetrieveNNCachedPolicies = 0;
        nbRetrieveNNValues = 0;
        nbGoodSelection = 0;
        nbRandomSelection = 0;
        nbRandomSelectionBestMoves = 0;
        maxRandomSelectionBestMoves = 0;
        minRandomSelectionBestMoves = Integer.MAX_VALUE;
        nbSubmitJobs = 0;
    }

    public void clearEachGame() {
        totalWinNodes = 0;
        totalLostNodes = 0;
        totalDrawnNodes = 0;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\n----------------------------------------------------------------------------------------------------------\n");
        sb.append(String.format("| %12s | %12s | %12s | %12s | %12s | %12s | %12s |\n",
                "Calls","Play","PosMoves","SubmitJobs","NNCacheVal","NNCachePol","NNValues"));
        sb.append(String.format("| %12d | %12d | %12d | %12d | %12d | %12d | %12d |\n",
                nbCalls,nbPlay,nbPossibleMoves,nbSubmitJobs,nbRetrieveNNCachedValues,nbRetrieveNNCachedPolicies,nbRetrieveNNValues));
        sb.append("----------------------------------------------------------------------------------------------------------\n");
        sb.append(String.format("| %12s | %12s | %12s | %12s | %12s | %12s | %12s |\n",
                "GoodSelect","maxRndSelect","minRndSelect","RndSelect","RndSelBest","",""));
        sb.append(String.format("| %12d | %12d | %12d | %12d | %12d | %12s | %12s |",
                nbGoodSelection,maxRandomSelectionBestMoves,minRandomSelectionBestMoves,nbRandomSelection,nbRandomSelectionBestMoves,"",""));
        return sb.toString();
    }

    public void incNodes(CacheValues cacheValues) {
        this.totalWinNodes += cacheValues.getWinCacheValue().getNbNodes();
        this.totalLostNodes += cacheValues.getLostCacheValue().getNbNodes();
        this.totalDrawnNodes += cacheValues.getDrawnCacheValue().getNbNodes();
    }
}
