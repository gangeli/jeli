package org.goobs.classify;

import java.util.HashMap;

import org.goobs.foreign.Counter;
import org.goobs.foreign.Indexer;
import org.goobs.utils.Tree;


public class ArrayFeatureVector <F> implements FeatureVector<F>{
	
	public static class ArrayFeatureFactory <F> implements FeatureFactory<F>{
		private final Indexer <F> indexer = new Indexer<F> ();
		@Override
		public FeatureVector <F> newTrainFeature(Counter <F> features){
			return new ArrayFeatureVector<F>(features, indexer, true);
		}
		@Override
		public FeatureVector <F> newTestFeature(Counter <F> features){
			return new ArrayFeatureVector<F>(features, indexer, false);
		}
		@Override
		public int numFeatures(){
			return indexer.size();
		}
		@Override
		public F getFeature(int featureIndex) {
			return indexer.get(featureIndex);
		}
		@Override
		public Tree<MemoryReport> dumpMemoryUsage(int minUse) {
			int size = estimateMemoryUsage();
			if(size >= minUse){
				return new Tree<MemoryReport>(new MemoryReport(size, "ArrayFeatureFactory"));
			}else{
				return new Tree<MemoryReport>();
			}
		}
		@Override
		public int estimateMemoryUsage() {
			int size = OBJ_OVERHEAD;
			size += indexer.size() * ((INT_SIZE) + (INT_SIZE + 10*CHR_SIZE));	//indexer (list, hashmap)
			return size;
		}
		@Override
		public Counter<F> weightCounter(double[] weights) {
			if(numFeatures() != weights.length){
				throw new IllegalArgumentException("Feature counts and weight vector do not match");
			}
			Counter <F> rtn = new Counter<F>();
			for(int i=0; i<numFeatures(); i++){
				rtn.setCount(indexer.get(i), weights[i]);
			}
			return rtn;
		}
		@Override
		public int getIndex(F featureValue) {
			return indexer.indexOf(featureValue);
		}
	}

	private Indexer <F> indexer;
	private final double[] features;
	private final int[] globalIndexes;
	
	private HashMap <Integer, F> origFeatureName = null;
	
	private ArrayFeatureVector(Counter<F> features, Indexer <F> indexer, boolean allowNewFeatures) {
		//(variables)
		this.features = new double[features.keySet().size()];
		this.globalIndexes = new int[features.keySet().size()];
		this.indexer = indexer;
		//(store features)
		int i = 0;
		for(F key : features.keySet()){
			int index = -1;
			if(allowNewFeatures){
				index = indexer.addAndGetIndex(key);
			}else{
				//(unk feature)
				index = indexer.indexOf(key);
				if(index < 0) index = UNK_GLOBAL_INDEX;
				//(remember feature name for printing)
				if(origFeatureName == null) origFeatureName = new HashMap <Integer, F> ();
				origFeatureName.put(i, key);
//				if(index < 0) throw new IllegalArgumentException("Feature never seen in training set: " + key);
			}
			this.features[i] = features.getCount(key);
			this.globalIndexes[i] = index;
			i+=1;
		}
	}

	@Override
	public Counter<F> getFeatures() {
		Counter <F> rtn = new Counter<F>();
		for(int index = 0; index < features.length; index++){
			if(globalIndexes[index] == UNK_GLOBAL_INDEX){
				rtn.setCount(origFeatureName.get(index), features[index]);
			}else{
				rtn.setCount(indexer.get(globalIndexes[index]), features[index]);
			}
		}
		return rtn;
	}

	@Override
	public double[] getFeatureVector() {
		return features;
	}
	
	
	@Override
	public int numFeatures() {
		return features.length;
	}

	@Override
	public double getCount(int internalIndex) {
		return features[internalIndex];
	}

	@Override
	public int globalIndex(int internalIndex) {
		return globalIndexes[internalIndex];
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
		size += REF_SIZE;	//indexer reference
		size += features.length*DBL_SIZE; //features
		size += globalIndexes.length*INT_SIZE; //globalIndexes
		if(origFeatureName != null){
			size += origFeatureName.keySet().size() * (INT_SIZE + REF_SIZE); //origFeatureName
		}
		return size;
	}

	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("[ ");
		for(int i=0; i<features.length; i++){
			String feat = "";
			if(globalIndexes[i] == UNK_GLOBAL_INDEX){
				feat = origFeatureName.get(i).toString();
			}else{
				feat = indexer.get(globalIndexes[i]).toString();
			}
			b.append(feat).append(":");
			b.append(features[i]).append("  ");
		}
		b.append("]");
		return b.toString();
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
