package com.aquila.chess.strategy.mcts;

public class ResultGame {
	public ResultGame(final int whiteWin, final int blackWin) {
		if (whiteWin == 1 && blackWin == 1)
			reward = 0.0;
		else if (whiteWin == 1)
			reward = 1.0; // 1
		else if (blackWin == 1)
			reward = -1.0; // 1
	}

	/**
	 * <ul>
	 *     <li>white win -> +1</li>
	 *     <li>black win -> 1</li>
	 *     <li>draw      -> 0</li>
	 * </ul>
	 */
	public double reward;
}
