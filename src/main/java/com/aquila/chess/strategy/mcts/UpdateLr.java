package com.aquila.chess.strategy.mcts;

/**
 * @formatter:off
 * Example of LR: 0.2, 0.02, 0.002 
 * @formatter:on
 */
@FunctionalInterface
public interface UpdateLr {

	double update(int nbGames);

	/**
	 * Create a default UpdateLr from alphazero settings
	 * @return
	 */
	static UpdateLr createDefault() {
		return new UpdateLr() {
			@Override
			public double update(int nbGames) {
				if (nbGames < 100)
					return 1e-4F;
				else if (nbGames < 300)
					return 1e-5F;
				else if (nbGames < 500)
					return 1e-6F;
				else
					return 1e-7F;
			}
		};
	}
}