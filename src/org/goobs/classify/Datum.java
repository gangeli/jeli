package org.goobs.classify;

import org.goobs.utils.MemoryAccountable;
import org.goobs.utils.Tree;

public class Datum<F> implements MemoryAccountable{
	//(lazy variables)
	@SuppressWarnings("rawtypes")
	private LabeledInstance instance;
	@SuppressWarnings("rawtypes")
	private FeatureExtractor featureExtractor; 
	@SuppressWarnings("rawtypes")
	private FeatureFactory featureFact;
	//(non-lazy variables)
	private int output = -1;
	private FeatureVector<F>[] possibleOutputs = null;

	public Datum(FeatureVector<F>[] possibleOutputs, int output) {
		if(output < 0 || output > possibleOutputs.length){
			throw new IllegalArgumentException("Possible output out of range: " + output);
		}
		this.output = output;
		this.possibleOutputs = possibleOutputs;
	}

	public <I,L> Datum(
		LabeledInstance<I,L> instance, 
		FeatureExtractor<I,F,L> featureExtractor, 
		FeatureFactory<F> featureFact){
		this.instance = instance;
		this.featureExtractor = featureExtractor;
		this.featureFact = featureFact;
	}

	public int numPossibleOutputs() {
		if(possibleOutputs == null){
			//lazy version
			return instance.getPossbleOutputs().length;
		}else{
			return possibleOutputs.length;
		}
		 
	}

	@SuppressWarnings("unchecked")
	public FeatureVector<F> output() {
		if(possibleOutputs == null){
			//lazy version
			return featureFact.newTrainFeature(featureExtractor
					.extractFeatures(instance.getInput(), 
					instance.getPossbleOutputs()[instance.getOutputIndex()]));
		}else{
			return possibleOutputs[output];
		}
	}

	public int outputIndex() {
		if(output < 0){
			//lazy version
			return instance.getOutputIndex();
		}else{
			return output;
		}
	}

	@SuppressWarnings("unchecked")
	public FeatureVector<F> possibleOutput(int index) {
		if(possibleOutputs == null){
			//lazy version
			return featureFact.newTrainFeature(featureExtractor
					.extractFeatures(instance.getInput(), 
					instance.getPossbleOutputs()[index]));
		}else{
			return possibleOutputs[index];
		}
	}
	
	@Override
	public Tree<MemoryReport> dumpMemoryUsage(int minUse) {
		int size = estimateMemoryUsage();
		if(size > minUse){
			Tree <MemoryReport> rtn = new Tree<MemoryReport>(new MemoryReport(size, "datum"));
			if(possibleOutputs != null){
				for(FeatureVector <F> vec : possibleOutputs){
					rtn.addChild(vec.dumpMemoryUsage(minUse));
				}
			}
			return rtn;
		}else{
			return new Tree<MemoryReport>();
		}
	}

	@Override
	public int estimateMemoryUsage() {
		int size = OBJ_OVERHEAD;
		size += INT_SIZE; //output
		if(possibleOutputs != null){
			for(FeatureVector<F> vec : possibleOutputs){
				size += vec.estimateMemoryUsage();
			}
		}
		return size;
	}
	
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("DATUM\n");
		if(possibleOutputs != null){
			for(int i=0; i<possibleOutputs.length; i++){
				if(i==output) b.append("   *"); else b.append("    ");
				b.append(possibleOutputs[i]);
				b.append("\n");
			}
		}else{
			b.append("   [lazy: ").append(instance.toString()).append("\n");
		}
		return b.toString();
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o){
		if(o instanceof Datum){
			@SuppressWarnings("rawtypes")
			Datum<F> other = (Datum) o;
			if(this.numPossibleOutputs() != other.numPossibleOutputs()) return false;
			for(int i=0; i < this.numPossibleOutputs(); i++){
				FeatureVector<F> feat1 = this.possibleOutput(i);
				FeatureVector<F> feat2 = other.possibleOutput(i);
				if(!feat1.equals(feat2)) return false;
			}
			return true;
		}
		return false;
	}
	
}
