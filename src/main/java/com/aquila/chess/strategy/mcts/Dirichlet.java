package com.aquila.chess.strategy.mcts;

/**
 *
 */
@FunctionalInterface
public interface Dirichlet {

    boolean update(int nbStep);

    /**
     * Create a default UpdateLr from alphazero settings
     *
     * @return
     */
    default Dirichlet createDefault() {
        return new Dirichlet() {
            @Override
            public boolean update(int nbStep) {
                return true;
            }
        };
    }
}