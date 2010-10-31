package org.goobs.testing;

import org.goobs.exec.Log;

public class DatasetSlice <D extends Datum> extends Dataset <D>{

	private Dataset<D> root;
	private int startInclusive;
	private int stopExclusive;
	private boolean opposite;
	
	protected DatasetSlice(Dataset<D> root, int startInclusive, int stopExclusive, boolean opposite){
		//(set variables)
		this.root = root;
		this.startInclusive = startInclusive;
		this.stopExclusive = stopExclusive;
		this.opposite = opposite;
		//(error checks)
		if(stopExclusive-startInclusive < 0){
			throw new IllegalArgumentException("Database slice ranges are invalid (bad order): " + startInclusive + "-"+stopExclusive);
		}
		if(stopExclusive-startInclusive == 0){
			Log.warn("Dataset", "Dataset slice has zero size: " + startInclusive + "-" + stopExclusive);
		}
		if(stopExclusive-startInclusive > root.numExamples()){
			throw new IllegalArgumentException("Database slice ranges are invalid (too large): " + startInclusive + "-"+stopExclusive);
		}
		if(stopExclusive > root.numExamples()){
			throw new IllegalArgumentException("Taking a slice which goes beyond the root's numExamples");
		}
	}
	
	@Override
	public int numExamples() {
		if(opposite){
			return root.numExamples() - (stopExclusive-startInclusive);
		}else{
			return stopExclusive - startInclusive;
		}
		
	}

	@Override
	public D get(int id) {
		if(opposite){
			if(id >= startInclusive){
				return root.get(id + (stopExclusive-startInclusive) );
			} else {
				return root.get(id);
			}
		}else{
			return root.get(startInclusive + id);
		}
	}

}
