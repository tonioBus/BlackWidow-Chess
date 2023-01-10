package com.aquila.chess.manager;

public class Sequence {
	public final long startDate;
	public int nbStep;

	public Sequence() {
		startDate = System.currentTimeMillis();
		nbStep=0;
	}

	public void play() {
		nbStep++;
	}

}