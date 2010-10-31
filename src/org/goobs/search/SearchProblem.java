package org.goobs.search;

import java.util.Iterator;

import org.goobs.foreign.Counter;

public interface SearchProblem {
	public static final int NONE 			= 0;
	public static final int DEBUG 			= 1;
	public static final int LEAST_COST 		= 2;
	public static final int GREATEST_SCORE 	= 4;
	public static final int MEMOIZE			= 8;
	
	public Counter <SearchState> search(SearchState[] startStates, int flags);
	
	public Iterator <Counter<SearchState>> searchAsync(SearchState[] startStates, int flags);

	public void capMemory(long bytes);
}
