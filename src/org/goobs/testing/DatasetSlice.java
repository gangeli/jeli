package org.goobs.testing;

import org.goobs.exec.Log;
import org.goobs.util.Range;

public class DatasetSlice <D extends Datum> extends Dataset <D>{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1089678853208585396L;
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
		Range parent = root.range();
		if(!parent.inRange(startInclusive) || !parent.inRange(stopExclusive-1)){
			throw new IllegalArgumentException("Database slice ranges are invalid: slice=" + this.range() + "; parent="+parent);
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

	@Override
	public Range range() {
		return new Range(0,stopExclusive-startInclusive);
	}

}
