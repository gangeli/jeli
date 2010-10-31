package org.goobs.tests;

import java.util.Iterator;

import static org.junit.Assert.*;
import org.junit.*;

import org.goobs.foreign.Counter;
import org.goobs.functional.Function2;
import org.goobs.search.AStarSearch;
import org.goobs.search.SearchProblem;
import org.goobs.search.SearchState;


public class SearchTest{

	private final int size = 100;
	
	private class CartesianNode implements SearchState{
		private int x,y;
		private CartesianNode last = null;
		private CartesianNode(int x, int y){
			this.x = x; this.y = y;
		}
		private CartesianNode(int x, int y, CartesianNode last){
			this(x,y);
			this.last = last;
		}
		@Override
		public Counter<SearchState> children(double needToBeat) {
			Counter<SearchState> counts = new Counter<SearchState>();
			if(x > size || y > size){
				counts.incrementCount(new CartesianNode(x-10, y, this), 1.0);
				counts.incrementCount(new CartesianNode(x+10, y, this), 1.0);
				counts.incrementCount(new CartesianNode(x, y-10, this), 1.0);
				counts.incrementCount(new CartesianNode(x, y+10, this), 1.0);
			}else{
				counts.incrementCount(new CartesianNode(x-1, y, this), 1.0);
				counts.incrementCount(new CartesianNode(x+1, y, this), 1.0);
				counts.incrementCount(new CartesianNode(x, y-1, this), 1.0);
				counts.incrementCount(new CartesianNode(x, y+1, this), 1.0);
			}
			return counts;
		}
		@Override
		public boolean isStopState() {
			return x == 0 && y == 0;
		}
		@Override
		public boolean equals(Object o){
			if(o instanceof CartesianNode){
				CartesianNode n = (CartesianNode) o;
				return n.x == x && n.y == y;
			}
			return false;
		}
		@Override
		public int hashCode(){
			return x & y;
		}
		@Override
		public String toString(){
			return "(" + x + "," + y + ")";
		}
		public String backtrack(){
			return "(" + x + "," + y + ") <- " + (last == null ? "START" : last.backtrack());
		}
	}
	
	
	@Test
	public void testAStarSimple(){
		AStarSearch<CartesianNode> search = new AStarSearch<CartesianNode>();
		search.setHeuristic(new Function2<CartesianNode,Double, Double>(){
			private static final long serialVersionUID = 1277867034141813429L;
			@Override
			public Double eval(CartesianNode input, Double distSoFar) {
				return Math.sqrt(input.x*input.x + input.y*input.y);
			}
		});
		Iterator <Counter<SearchState>> iter = search.searchAsync(
				new SearchState[]{ new CartesianNode(size,size) }, 
				SearchProblem.LEAST_COST | SearchProblem.MEMOIZE
				);
		assertTrue(iter.hasNext());
		Counter<SearchState> term = iter.next();
		SearchState sol = term.argMax();
		double score = term.getCount(sol);
		System.out.println(((CartesianNode) sol).backtrack());
		assertEquals(103.0, score, 0.0);
	}
}
