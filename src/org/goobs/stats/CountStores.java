package org.goobs.stats;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.NoSuchElementException;

public class CountStores {

	public static <D> CountStore<D> MAP(){
		return new CountStore<D>(){
			private Map<D,Double> map = new HashMap<D,Double>();
			private double totalCount = 0.0;
			@Override public double totalCount(){ return totalCount; }
			@Override
			public double getCount(D key) {
				Double count = map.get(key);
				if(count == null){
					return 0.0;
				} else {
					return count;
				}
			}
			@Override
			public void setCount(D key, double count) {
				totalCount += (count - getCount(key));
				map.put(key, count);
			}
			@Override
			public CountStore<D> emptyCopy() { return MAP(); }
			@Override
			public CountStore<D> clone() throws CloneNotSupportedException {
				CountStore<D> cloned = emptyCopy();
				for(D key : map.keySet()){
					cloned.setCount(key, map.get(key));
				}
				return cloned;
			}
			@Override
			public CountStore<D> clear() {
				map.clear();
				totalCount = 0.0;
				return this;
			}

			@Override
			public Iterator<D> iterator() {
				return map.keySet().iterator();
			}
		};
	}


	public static CountStore<Integer> ARRAY(final int capacity){
		return new CountStore<Integer>(){
			private double[] counts = new double[capacity];
			@Override public double getCount(Integer key) { return counts[key]; }
			@Override	public void setCount(Integer key, double count) { counts[key] = count; }
			@Override public CountStore<Integer> emptyCopy() { return ARRAY(capacity); }
			@Override
			public double totalCount(){
				double count = 0.0;
				for(int i=0; i<counts.length; i++){
					count += counts[i];
				}
				return count;
			}
			@Override
			public CountStore<Integer> clone() throws CloneNotSupportedException {
				CountStore<Integer> cloned = emptyCopy();
				for(int i=0; i<counts.length; i++){
					cloned.setCount(i,counts[i]);
				}
				return cloned;
			}
			@Override
			public CountStore<Integer> clear() {
				for(int i=0; i<counts.length; i++){
					counts[i] = 0.0;
				}
				return this;
			}

			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>(){
					private int nextIndex = 0;
					@Override
					public boolean hasNext() {
						return nextIndex < counts.length;
					}
					@Override
					public Integer next() {
						if(nextIndex >= counts.length){ throw new NoSuchElementException(); }
						int rtn = nextIndex;
						nextIndex += 1;
						return rtn;
					}
					@Override
					public void remove() { throw new RuntimeException("NOT IMPLEMENTED"); }
				};
			}
		};
	}

}
