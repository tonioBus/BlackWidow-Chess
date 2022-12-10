package com.aquilla.chess.strategy.mcts;

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
        return String.format("calls:%d play:%d possibleMovesCalls:%d SubmitJobs:%d NNCached:%d NNPolicies:%d NNretrieved:%d\nnbGoodSelection:%d maxRandomSelection:%d minRandomSelection:%d nbRandomSelection:%d nbRandomSelectionBestMoves:%d average:%f",
                nbCalls, nbPlay, nbPossibleMoves, nbSubmitJobs, nbRetrieveNNCachedValues, nbRetrieveNNCachedPolicies,
                nbRetrieveNNValues, nbGoodSelection, maxRandomSelectionBestMoves, minRandomSelectionBestMoves, nbRandomSelection, nbRandomSelectionBestMoves, nbRandomSelection == 0 ? 100000.0 : nbRandomSelectionBestMoves / nbRandomSelection);
    }
}
