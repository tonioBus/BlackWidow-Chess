package com.aquila.chess.strategy.mcts;

public class ResultGame {
    public ResultGame(final int whiteWin, final int blackWin) {
        assert (whiteWin == 0 || whiteWin == 1);
        assert (blackWin == 0 || blackWin == 1);
        if (whiteWin == 1 && blackWin == 1)
            reward = 0.0F;
        else if (whiteWin == 1)
            reward = 1.0F; // 1
        else if (blackWin == 1)
            reward = -1.0F; // 1
        else reward = 0.0F;
    }

    /**
     * <ul>
     *     <li>white win -> +1</li>
     *     <li>black win -> 1</li>
     *     <li>draw      -> 0</li>
     * </ul>
     */
    public final float reward;
}
