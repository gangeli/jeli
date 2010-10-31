package org.goobs.classify;

import org.goobs.foreign.Counter;

public abstract class FeatureExtractor<Input, Encoding, Output> {
	
	public Counter<Encoding> extractFeatures(Input input, Output output){
		Counter <Encoding> in = new Counter <Encoding> ();
		Counter <Encoding> out = new Counter <Encoding> ();
		fillFeatures(input, in, output, out);
		
		Counter <Encoding> rtn = new Counter <Encoding> ();
		for(Encoding e1 : in.keySet()){
			for(Encoding e2 : out.keySet()){
				rtn.setCount(concat(e2, e1), in.getCount(e1) * out.getCount(e2));
			}
		}
		
		return rtn;
	}
	
	protected abstract void fillFeatures(
			Input input, 
			Counter <Encoding> inFeatures, 
			Output output, 
			Counter <Encoding> outFeatures);
	protected abstract Encoding concat(Encoding a, Encoding b);

}
