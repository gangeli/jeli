package org.goobs.search;

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.text.DecimalFormat;

import org.goobs.exec.Log;
import org.goobs.foreign.Counter;
import org.goobs.utils.SingletonIterator;

public class KBestSearch implements SearchProblem{

	public static final int PRUNE_TOP = 1024;

	private int k;
	private int minK;
	private double keepPercent;
	private TaggedState[] parents;
	private PriorityQueue<TaggedState> children;
	private TaggedState[] childPool;
	private int nextChild;

	private class TaggedState implements Comparable<TaggedState>{
		private SearchState state;
		private double score;
		private TaggedState(SearchState state, double score){
			this.state = state;
			this.score = score;
		}
		private void clear(){ setTo(null, 0, true); }
		private boolean exists(){ return state != null; }
		private boolean isDone(){ return state.isStopState(); }
		private TaggedState setTo(SearchState state, double score, boolean leastCost){
			this.state = state;
			this.setScore(score, leastCost);
			return this;
		}
		private double getScore(boolean leastCost){
			return leastCost ? -score : score;
		}
		private void setScore(double score, boolean leastCost){
			this.score = (leastCost ? -score : score);
		}
		@Override
		public int compareTo(TaggedState arg0) {
			if(this.score > arg0.score){
				return 1;
			}else if(this.score < arg0.score){
				return -1;
			}else{
				return 0;
			}
		}
		@Override
		public String toString(){
			return "(" + score + "):" + state;
		}
		public String niceToString(boolean leastCost){
			DecimalFormat df = new DecimalFormat("0.000");
			return "(" + df.format(getScore(leastCost)) + "): " + state;
		}
	}
	
	public KBestSearch(int k){
		this(k, k, 1.0);
	}

	public KBestSearch(int minK, int maxK, double keepPercent){
		this.k = maxK;
		this.minK = minK;
		this.keepPercent = keepPercent;
		this.parents = new TaggedState[k];
		this.childPool = new TaggedState[k];
		for(int i=0; i<this.k; i++){
			parents[i] = new TaggedState(null,0);
			childPool[i] = new TaggedState(null, 0);
		}
		this.children = new PriorityQueue<TaggedState>(k);
	}

	public void setK(int k){
		this.k = k;
	}
	public void setKRange(int minK, int maxK){
		this.k = maxK;
		this.minK = minK;
	}
	public void setKeepPercent(double keepPercent){
		this.keepPercent = keepPercent;
	}
	
	/**
	*	Place the state in the queue iff it is in the top k
	*	scoring terms.
	*/
	private double add(SearchState state, boolean leastCost, double totalScore, double lastNeedToBeat){
		double needToBeat = lastNeedToBeat;
		if(children.size() < k){
			//we haven't filled up our k-best list yet
			TaggedState term = childPool[nextChild];
			term.setTo(state, totalScore, leastCost);
			children.add(term);
			nextChild++;
		}else{
			TaggedState term = children.peek();
			if(term.getScore(leastCost) < totalScore){
				//replace the lowest term with this one
				term = children.poll();
				term.setTo(state, totalScore, leastCost);
				children.add(term);
				needToBeat = children.peek().getScore(leastCost);
			}else{
				//keep the term that was there
				needToBeat = term.getScore(leastCost);
			}
		}
		if(children.size() > k){ throw new IllegalStateException("We're growwwwinnng! " + children.size() + " > " + k); }
		return needToBeat;
	}
	
	private boolean iterate(boolean pruneTop, boolean leastCost){
		boolean isDone = true;
		
		//--Create the children
		double needToBeat = leastCost ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
		children.clear();
		nextChild = 0;
		for(int i=0; i<k; i++){ childPool[i].clear(); }	//clear children
		for(int i=0; i<k; i++){
			TaggedState state = parents[i];
			if(state == null){ throw new IllegalStateException("Parent array should not contain null elements");}
			if(state.exists()){
				if(state.isDone()){
					//we pass this state along, since it's done
					needToBeat = add(state.state, leastCost, state.getScore(leastCost), needToBeat);
				}else{
					isDone = false;	//we still have children
					Counter <SearchState> children = state.state.children(needToBeat - state.getScore(leastCost));
					for(SearchState s : children.keySet()){
						// priority queue will keep the least elements
						needToBeat = add(s, leastCost, state.getScore(leastCost) + children.getCount(s), needToBeat);
					}
				}
			}
		}
		//--Make the children the parents
		TaggedState[] deadNodes = parents;	//the childCache will be the nodes in the new parents
		parents = childPool;
		childPool = deadNodes;
		//--Keep top percent terms only
		if(pruneTop){
			//(get the total count)
			double totalCount = 0;
			int parity = 0;
			for(TaggedState t : deadNodes){
				double score = t.getScore(leastCost);
				if((score < 0 && parity > 0) || (score > 0 && parity < 0)) 
					throw new IllegalStateException("Cannot normalize search state counts: both positive and negative values");
				totalCount += score;
			}
			//(prune the nodes)
			double probSoFar = 0;
			int size = children.size();
			int cleared = 0;
			while(!children.isEmpty()){
				TaggedState state = children.poll();
				probSoFar += ( (totalCount == 0) ? 0 : (state.getScore(leastCost)/totalCount) );
				if( (size-cleared > this.minK) && (probSoFar < (1.0-keepPercent)) ){
					//clear the low probability states
					state.clear();
					cleared += 1;
				}else{
					//keep the state
				}
			}
		}
		
		return !isDone;
	}
	
	private final boolean flagSet(int flags, int flag){
		return (flags & flag) != 0;
	}

	public int getK(){
		return k;
	}

	private void printArray(TaggedState[] toPrint, boolean leastCost){
		int maxPrint = 5;
		//(get top maxPrint terms)
		PriorityQueue <TaggedState> pq = new PriorityQueue<TaggedState>();
		double total = 0;
		double inQueue = 0;
		int nonNull = 0;
		for(TaggedState ts : toPrint){
			if(ts.state != null && pq.size() < maxPrint){
				pq.add(ts);
				inQueue += ts.getScore(leastCost);
			}else{
				TaggedState term = pq.peek();
				if(ts.state != null && term.getScore(leastCost) < ts.getScore(leastCost)){
					inQueue -= pq.poll().getScore(leastCost);
					pq.add(ts);
					inQueue += ts.getScore(leastCost);
				}
			}
			if(ts.state != null){
				total += ts.getScore(leastCost);
				nonNull += 1;
			}
		}
		//(print the terms)
		LinkedList <TaggedState> reversed = new LinkedList <TaggedState>();
		while(!pq.isEmpty()){
			reversed.addLast(pq.poll());
		}
		StringBuilder b = new StringBuilder();
		for(TaggedState ts : reversed){
			b.append(ts.niceToString(leastCost));
			b.append(",  ");
		}
		double remaining = ((total - inQueue) / total);
		DecimalFormat df = new DecimalFormat("0.000");
		b.append("...[" + df.format(remaining) + " mass / " + (nonNull - maxPrint) + " terms remaining]");
		Log.log(b);
	}

	@Override
	public void capMemory(long bytes){
		if(k > bytes / 8){
			throw new IllegalStateException("KBestSearch exceeds memory cap");
		}
	}

	@Override
	public Counter<SearchState> search(SearchState[] startStates, int flags) {
		if(flagSet(flags, MEMOIZE)){ throw new SearchException("MEMOIZE flag not applicable for GreedySearch"); }
		//--Flags
		boolean pruneTop = flagSet(flags, PRUNE_TOP);
		boolean debug = flagSet(flags, DEBUG);
		boolean leastCost = flagSet(flags, LEAST_COST);
		if(leastCost && flagSet(flags, GREATEST_SCORE))
			throw new IllegalArgumentException("Contradictory search flags: LEAST_COST and GREATEST_SCORE");
		if(!leastCost && !flagSet(flags, GREATEST_SCORE)) 
			throw new IllegalArgumentException("Search must either be LEAST_COST or GREATEST_SCORE");
		//--Initialize the start states
		for(int i=0; i<k; i++){
			parents[i].clear();
			childPool[i].clear();
		}
		if(startStates.length > k){ throw new IllegalArgumentException("More start states than k"); }
		for(int i=0; i<startStates.length; i++){
			parents[i].setTo(startStates[i], 0, leastCost);
		}
		
		//--Search
		int iter = 0;
		if(debug) printArray(parents, leastCost);
		while(iterate(pruneTop, leastCost)){
			//optionally print stuff here
			iter += 1;
			if(debug) printArray(parents, leastCost);
		}
		
		//--Result
		Counter<SearchState> rtn = new Counter<SearchState>();
		for(int i=0; i<parents.length; i++){
			if(parents[i].exists()){
				if(!parents[i].isDone()){ throw new IllegalStateException("Returning nodes that are not in the stop state"); }
				rtn.setCount(parents[i].state, parents[i].getScore(leastCost));
				
			}
		}
		return rtn;
	}
	
	@Override
	public Iterator<Counter<SearchState>> searchAsync(
			SearchState[] startStates, int flags) {
		return SingletonIterator.make(search(startStates, flags));
	}

}
