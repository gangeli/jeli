package org.goobs.testing;

import org.goobs.util.Range;

public class ToyDataset<D extends Datum> extends Dataset<D>{
	private static final long serialVersionUID = 1991110523181106998L;
	private D[] data;
	
	public ToyDataset(D[] data){
		this.data = data;
	}
	@Override
	public int numExamples() {
		return data.length;
	}
	@Override
	public D get(int id) {
		return data[id];
	}
	@Override
	public Range range() {
		return new Range(0,data.length);
	}
}
