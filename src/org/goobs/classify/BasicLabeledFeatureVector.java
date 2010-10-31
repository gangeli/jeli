package org.goobs.classify;


import org.goobs.foreign.Counter;
import org.goobs.utils.Tree;

/**
 * A minimal implementation of a labeled datum, wrapping a list of features and
 * a label.
 * 
 * @author Dan Klein
 * Modifications by Gabor Angeli
 */
public class BasicLabeledFeatureVector<F, L> implements
		LabeledFeatureVector<F, L> {
	private L label;
	private Counter<F> features;

	public L getLabel() {
		return label;
	}

	public Counter<F> getFeatures() {
		return features;
	}

	public String toString() {
		return "<" + getLabel() + " : " + getFeatures().toString() + ">";
	}

	public BasicLabeledFeatureVector(L label, Counter<F> features) {
		this.label = label;
		this.features = features;
	}

	@Override
	public double getCount(int internalIndex) {
		throw new IllegalArgumentException("Basic Feature Vector does not have internal indexes");
	}

	@Override
	public double[] getFeatureVector() {
		throw new IllegalArgumentException("Basic Feature Vector should not return feature vectors");
	}

	@Override
	public int globalIndex(int internalIndex) {
		throw new IllegalArgumentException("Basic Feature Vector does not have internal indexes");
	}

	@Override
	public int numFeatures() {
		return features.size();
	}

	@Override
	public Tree<MemoryReport> dumpMemoryUsage(int minUse) {
		int usage = estimateMemoryUsage();
		if(usage >= minUse){
			return new Tree<MemoryReport>(new MemoryReport(usage, "feature vector"));
		}else{
			return new Tree<MemoryReport>();
		}
	}

	@Override
	public int estimateMemoryUsage() {
		int size = OBJ_OVERHEAD;
		size += 10*CHR_SIZE;	//label
		size += features.keySet().size() * (10*CHR_SIZE + DBL_SIZE); //features
		return size;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o){
		if(o instanceof FeatureVector){
			FeatureVector<F> vector = (FeatureVector<F>) o;
			if(vector.numFeatures() == this.numFeatures()){
				for(int i=0; i<this.numFeatures(); i++){
					if(this.getCount(i) != vector.getCount(i) &&
							this.globalIndex(i) == vector.globalIndex(i)){ 
						return false; 
					}
				}
				return true;
			}
		}
		return false;
	}
}
