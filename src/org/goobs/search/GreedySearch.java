package org.goobs.search;

import java.util.Iterator;

import org.goobs.foreign.Counter;
import org.goobs.utils.Pair;
import org.goobs.utils.SingletonIterator;

public class GreedySearch implements SearchProblem{

	private SearchState current;
	private double logCost;
	
	private void iterate(boolean leastCost) {
		Counter <SearchState> children = current.children(leastCost ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
		if(children.isEmpty()){
			throw new SearchException("Greedy search hit a dead end at state: " + current);
		}
		current = null;
		if(leastCost) {
			current = children.argMin();
		} else {
			current = children.argMax();
		}
		logCost += Math.log(children.getCount(current));
	}
	
	public Pair<SearchState,Double> searchGreedy(SearchState start, boolean leastCost){
		current = start;
		logCost = 0;
		while(!current.isStopState()){
			iterate(leastCost);
		}
		return new Pair<SearchState,Double>(current, logCost);
	}
	
	private final boolean flagSet(int flags, int flag){
		return (flags & flag) != 0;
	}

	@Override
	public void capMemory(long bytes){
		//greedy search has no memory constraints
	}

	@Override
	public Counter<SearchState> search(SearchState[] startStates, int flags) {
		if(flagSet(flags, MEMOIZE)){ throw new SearchException("MEMOIZE flag not applicable for GreedySearch"); }
		boolean leastCost = flagSet(flags, LEAST_COST);
		if(flagSet(flags, GREATEST_SCORE)) throw new IllegalArgumentException("Contradictory search flags");
		if(startStates.length > 1){
			throw new IllegalArgumentException("Greedy search should only take one start state");
		}
		Pair<SearchState,Double> end = searchGreedy(startStates[0],leastCost);
		Counter<SearchState> rtn = new Counter<SearchState>();
		rtn.incrementCount(end.car(), end.cdr());
		return rtn;
	}
	
	@Override
	public Iterator<Counter<SearchState>> searchAsync(
			SearchState[] startStates, int flags) {
		return SingletonIterator.make(search(startStates, flags));
	}

}
