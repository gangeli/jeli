package org.goobs.distributions;

import org.goobs.foreign.Counter;
import org.goobs.utils.Pair;

public interface Particle <E,O>{
	
	public static final int INFINITE_DEPTH = Integer.MAX_VALUE;
	
	public static interface PartialFeatureExtractor<I,E,O>{
		public Counter<E> extractFeatures(I input, O output, int depth);
	}
	
	public O getContent();
	public Counter<E> features();
	public Pair< Particle<E,O>, Double > makeChild(Counter<E> weights);
	public boolean isTerminal(int depth);
	public Particle<E,O> copy();
	
}
