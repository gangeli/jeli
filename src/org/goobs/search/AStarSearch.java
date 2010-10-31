package org.goobs.search;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.goobs.exec.Log;
import org.goobs.foreign.Counter;
import org.goobs.functional.Function2;
import org.goobs.utils.Heap;
import org.goobs.utils.MaxHeap;
import org.goobs.utils.MinHeap;
import org.goobs.utils.Utils;
import org.goobs.utils.Stopwatch;

public class AStarSearch <E extends SearchState> implements SearchProblem{

	public final Function2<E,Double, Double> nullHeuristic = new Function2<E,Double, Double>(){
		private static final long serialVersionUID = 6810988601548959674L;
		@Override
		public Double eval(E input, Double distSoFar) {
			return 0.0;
		}
	};
	
	private Function2<E,Double, Double> heuristic;
	private long memoryCap = Long.MAX_VALUE;
	
	private class AStarIterator implements Iterator<Counter<SearchState>>{
	
		private Heap<E> queue = null;
		private Map<E,Double> bestDistances = null;
		private double needToBeat = 0;
		private int flags;
		
		private int solutionsFound = 0;
		private int globalIterations = 0;
		private E solution = null;
		private double solutionScore = -1.0;
		private Stopwatch watch = new Stopwatch();
		
		private AStarIterator(E startState, int flags){
			//--Process Flags
			this.flags = flags;
			if(flagSet(flags, SearchProblem.MEMOIZE)){ bestDistances = new HashMap<E,Double>(); }
			if(flagSet(flags, SearchProblem.GREATEST_SCORE)){
				queue = new MaxHeap<E>(16, memoryCap / 24);
				needToBeat = Double.NEGATIVE_INFINITY;
			}else if(flagSet(flags, SearchProblem.LEAST_COST)){
				queue = new MinHeap<E>(16, memoryCap / 24);
				needToBeat = Double.POSITIVE_INFINITY;
			}else{
				throw new IllegalArgumentException("Either the LEAST_COST or GREATEST_SCORE flag must be set");
			}
			//--Initialize Search State
			if(bestDistances == null){
				queue.push(startState, heuristic.eval(startState,0.0), 0.0);
			}else{
				queue.push(startState, heuristic.eval(startState,0.0));
				bestDistances.put(startState, 0.0);
			}
		}
		
		@SuppressWarnings("unchecked")
		private void nextSolution(){
			int i=0;
			watch.start();
			while(!queue.isEmpty()){
				i += 1;
				//(get candidate)
				double distSoFar = -1.0;
				if(bestDistances == null){ distSoFar = (Double) queue.peekExtra(); }
				double score = queue.peekScore();
				E term = queue.pop();
				if(bestDistances != null){ distSoFar = bestDistances.get(term); }
				//(debug)
				if(flagSet(flags, SearchProblem.DEBUG)){
					StringBuilder b = new StringBuilder();
					b.append("A* Iteration (")
						.append(solutionsFound).append("):[").append(globalIterations).append("]+").append(i)
						.append(": dist=").append(Utils.df.format(distSoFar))
						.append(", score=").append(Utils.df.format(score))
						.append(", term=").append(term);
					b.append(" [").append(Stopwatch.formatTimeDifference(watch.getElapsedTime())).append("]");
					Log.log(b.toString());
				}
				//(check for done)
				if(term.isStopState()){
					this.solution = term;
					this.solutionScore = score;
					if(heuristic.eval(term, distSoFar) != 0){ Log.warn("Heuristic on stop term is not zero (" + heuristic.eval(term, distSoFar) + "): term=" + term); }
					globalIterations += i;
					solutionsFound += 1;
					watch.stop();
					return;	//RETURN: success
				}
				//(add candidate's children)
				Counter<SearchState> children = term.children(needToBeat);
				for(SearchState c : children.keySet()){
					//(get child)
					E child = null;
					try {
						child = (E) c;
					} catch (ClassCastException e) {
						throw new IllegalArgumentException("Search state is incompatible with the heuristic: " + c);
					}
					double childDist = children.getCount(child);
					if(childDist < 0 && heuristic == nullHeuristic){
						Log.warn("Distances cannot be negative with the default (uniform cost) heuristic");
					}
					double tentativeDist = distSoFar + childDist;
					boolean tentativeIsBetter = false;
					//(check child distance)
					if(bestDistances == null){
						//(case: don't memoize)
						tentativeIsBetter = true;
					}else{
						//(case: memoize)
						if(!bestDistances.containsKey(child)){
							//case: never seen node before
							tentativeIsBetter = true;
						}else if(betterThan(tentativeDist, bestDistances.get(child))){
							//case: found shortest distance
							tentativeIsBetter = true;
						}else{
							//case: shorter path found
							tentativeIsBetter = false;
						}
					}
					//(add if applicable)
					if(tentativeIsBetter){
						double childScore = tentativeDist + heuristic.eval(child, tentativeDist);
						if(bestDistances != null){ 
							bestDistances.put(child, tentativeDist);
							queue.push(child, childScore);
						}else{
							queue.push(child, childScore, tentativeDist);
						}
					}
				}
			}
			watch.stop();
			return; //RETURN: failure
		}
		
		private boolean betterThan(double a, Double b){
			if(b == null) return true;
			if(flagSet(flags, SearchProblem.GREATEST_SCORE)){
				return a > b;
			}else{
				return b > a;
			}
		}
		
		@Override
		public boolean hasNext() {
			if(solution == null){
				nextSolution();
			}
			return this.solution != null;
		}
	
		@Override
		public Counter<SearchState> next() {
			if(hasNext()){
				Counter <SearchState> rtn = new Counter<SearchState>();
				rtn.incrementCount(this.solution, this.solutionScore);
				this.solution = null;
				return rtn;
			}else{
				throw new NoSuchElementException();
			}
		}
	
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
	}

	public AStarSearch(){
		this.heuristic = nullHeuristic;
	}
	
	public AStarSearch(Function2<E,Double, Double> heuristic){
		this.heuristic = heuristic;
	}
	
	public AStarSearch<E> setHeuristic(Function2<E,Double, Double> heuristic){
		this.heuristic = heuristic;
		return this;
	}

	@Override
	public void capMemory(long bytes){
		this.memoryCap = bytes;
	}
	
	@Override
	public Counter<SearchState> search(SearchState[] startStates, int flags) {
		Iterator<Counter<SearchState>> iter = searchAsync(startStates, flags);
		if(iter.hasNext()){
			return iter.next();
		}else{
			throw new SearchException("No solutions to A* search");
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Iterator<Counter<SearchState>> searchAsync(
			SearchState[] startStates, int flags) {
		//--Error Checks
		if(startStates.length != 1){
			throw new IllegalArgumentException("A* Search must have exactly one start state");
		}
		E state;
		try {
			state = (E) startStates[0];
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Start state is incompatible with the heuristic type");
		}
		//--Search
		return new AStarIterator(state, flags);
	}
	
	private final boolean flagSet(int flags, int flag){
		return (flags & flag) != 0;
	}

}
