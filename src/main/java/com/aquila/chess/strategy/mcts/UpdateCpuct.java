package com.aquila.chess.strategy.mcts;

/**
 * Example of tau: exp(-0.04x)/2
 * tau = Math.exp(-0.04 * nbStep) / 2;
 */
@FunctionalInterface
public interface UpdateCpuct {

	double update(int nbStep, int nbLegalMoves);

}