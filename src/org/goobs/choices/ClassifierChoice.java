package org.goobs.choices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.goobs.classify.ArrayFeatureVector;
import org.goobs.classify.FeatureExtractor;
import org.goobs.classify.FeatureFactory;
import org.goobs.classify.FeatureVector;
import org.goobs.classify.LabeledInstance;
import org.goobs.classify.ProbabilisticClassifier;
import org.goobs.classify.ProbabilisticClassifierFactory;
import org.goobs.classify.OutputInfo;
import org.goobs.foreign.Counter;
import org.goobs.utils.Pair;

public abstract class ClassifierChoice <Input,Encode,Output> implements AChoice <Input, Output>{

	/**
	 * The possible outputs of this choice
	 * @return An array of the possible outputs of this choice
	 */
	public abstract Output[] possibleOutputs(Input in);

	/**
	 * The input features
	 * @param in The history for the choice
	 * @return The features on the input
	 */
	public abstract Counter <Encode> inputFeatures(Input in, Output out);
	/**
	 * The output features
	 * @param in The history for the choice
	 * @param out The output to gather features on
	 * @return The features on the output
	 */
	public abstract Counter <Encode> outputFeatures(Input in, Output out);
	/**
	 * The joint features
	 * @param in The history for the choice
	 * @param out The output to gather features on
	 * @return The joint features for the datum
	 */
	public abstract Counter <Encode> jointFeatures(Input in, Output out);
	/**
	 *  A Concatenation implementation, to be used in creating
	 *  the cross product of the input and output features
	 * @param left
	 * @param right
	 * @return The concatenation of left and right, for some definition of concatenation
	 */
	public abstract Object concat(Encode left, Encode right);
	
	
	private FeatureFactory <Object> featFactory = new ArrayFeatureVector.ArrayFeatureFactory<Object>();
	private ProbabilisticClassifierFactory <Input,Object,Output> factory;
	
	protected ProbabilisticClassifier <Input,Object,Output> classifier = null;
	
	
	private class ChoiceFeatureExtractor extends FeatureExtractor<Input,Object,Output>{
		@Override
		public final Counter<Object> extractFeatures(Input input, Output output){
			Counter <Object> rtn = super.extractFeatures(input,output);
			rtn.incrementAll(jointFeatures(input, output));
			return rtn;
		}
		@SuppressWarnings("unchecked")
		@Override
		protected final Object concat(Object a, Object b) {
			return ClassifierChoice.this.concat((Encode) a, (Encode) b);
		}
		@SuppressWarnings("unchecked")
		@Override
		protected final void fillFeatures(Input input, Counter<Object> inFeatures,
				Output output, Counter<Object> outFeatures) {
			inFeatures.setTo((Counter<Object>)inputFeatures(input,output));
			outFeatures.setTo((Counter<Object>)outputFeatures(input, output));
		}
	}
	
	
	public ClassifierChoice(ProbabilisticClassifierFactory <Input,Object,Output> factory){
		this.factory = factory;
	}
	
	public void train(List <LabeledInstance<Input,Output>> data){
		System.out.println("Training on " + data.size() + " examples");
		classifier = factory.trainClassifier(data, new ChoiceFeatureExtractor());
	}
	
	public FeatureVector <Object> features(Input in, Output out){
		//--Get feature sections
		Counter <Encode> inF = inputFeatures(in, out);
		Counter <Encode> outF = outputFeatures(in, out);
		Counter <Encode> joint = jointFeatures(in, out);
		
		//--Combine into feature vector
		//(add joint features)
		Counter <Object> feats = new Counter <Object> ();
		feats.incrementAll(joint);
		//(determine outer and inner counter)
		Counter <Encode> outer = null;
		Counter <Encode> inner = null;
		if(inF.size() < outF.size()){
			outer = inF;
			inner = outF;
		}else{
			outer = outF;
			inner = inF;
		}
		//(join on the left and right features)
		for(Encode a : outer.keySet()){
			double aCount = outer.getCount(a);
			if(aCount == 0){
				continue;	//short cut
			}
			//inner loop
			for(Encode b : inner.keySet()){
				double bCount = inner.getCount(b);
				if(bCount != 0){
					Object elem = concat(a, b);
					feats.incrementCount(elem, aCount * bCount);
				}
			}
		}
		
		//--Return the joint vector
		return featFactory.newTrainFeature(feats);
	}
	
	
	public final Output infer(Input in){
		return getProbabilities(in).argMax();
	}
	
	public final Output sample(Input in){
		return getProbabilities(in).sample();
	}
	
	public final Counter <Output> getProbabilities(Input in, boolean prohibitStop){
		Counter <Output> rtn = getProbabilities(in);
		if(prohibitStop) rtn.removeKey(stopTerm());
		rtn.normalize();
		if(rtn.totalCount() == Double.NaN){
			//handle error
			System.err.println();
			System.err.println("PREPARING FOR ERROR");
			System.err.println("Input: " + in);
			System.err.println("Possible output size: " + possibleOutputs(in).length);
			throw new IllegalStateException("No good outputs for choice");
		}
		return rtn;
	}

	public Counter<Output> getProbabilities(Input in){
		return classifier.getProbabilities(in, possibleOutputCollection(in));
	}
	
	public final void inferAndProb(Input in, Pair <Output,Double> toFill, boolean prohibitStop){
		Counter <Output> rtn = getProbabilities(in, prohibitStop);
		Output out = rtn.argMax();
		Double prob = rtn.getCount(out);
		toFill.setCar(out);
		toFill.setCdr(prob);
	}
	public final void inferAndProb(Input in, Pair <Output,Double> toFill){
		inferAndProb(in, toFill, false);
	}
	
	public final void sampleAndProb(Input in, Pair <Output,Double> toFill, boolean prohibitStop){
		Counter <Output> rtn = getProbabilities(in, prohibitStop);
		Output out = rtn.sample();
		Double prob = rtn.getCount(out);
		toFill.setCar(out);
		toFill.setCdr(prob);
	}
	public final void sampleAndProb(Input in, Pair <Output,Double> toFill){
		sampleAndProb(in, toFill, false);
	}
		
	@Override
	public Output output(Input in) {
		return infer(in);
	}
	
	public final Collection<Output> possibleOutputCollection(Input in){
		ArrayList <Output> outs = new ArrayList <Output>();
		Output[] array = possibleOutputs(in);
		for(Output out : array){
			outs.add(out);
		}
		return outs;
	}


	protected Pair<OutputInfo<Object>,OutputInfo<Object>> getInfo(	Input input, 
																	Output guessOut, 
																	Output goldOut) {
		return classifier.getInfo(input, guessOut, goldOut, possibleOutputCollection(input));
	}
}
