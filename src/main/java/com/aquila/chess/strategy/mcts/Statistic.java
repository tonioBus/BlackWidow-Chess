package com.aquila.chess.strategy.mcts;

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

    public void clear() {
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
        nbSubmitJobs=0;
    }

    @Override
    public String toString() {
        return String.format("nbCalls:%d\n nbPlay:%d\n nbPossibleMoves:%d\n nbSubmitJobs:%d\n nbRetrieveNNCachedValues:%d\n nbRetrieveNNCachedPolicies:%d\n nbRetrieveNNValues:%d\n nbGoodSelection:%d\n MAXRandomSelectionBestMoves:%d\n MINRandomSelectionBestMoves:%d\n nbRandomSelection:%d\n nbRandomSelectionBestMoves:%d\n average:%f",
                nbCalls,
                nbPlay,
                nbPossibleMoves,
                nbSubmitJobs,
                nbRetrieveNNCachedValues,
                nbRetrieveNNCachedPolicies,
                nbRetrieveNNValues,
                nbGoodSelection,
                maxRandomSelectionBestMoves,
                minRandomSelectionBestMoves,
                nbRandomSelection,
                nbRandomSelectionBestMoves,
                nbRandomSelection == 0 ? Double.POSITIVE_INFINITY : nbRandomSelectionBestMoves / nbRandomSelection);
    }
}
