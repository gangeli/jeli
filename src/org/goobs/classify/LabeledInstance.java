package org.goobs.classify;

/**
 * LabeledInstances are input instances along with a label.
 * 
 * @author Dan Klein
 * Modifications by Gabor Angeli
 */
public class LabeledInstance<I, O> {
	private I input;
	private O[] outputs;
	private int actualOutput;

	public LabeledInstance(I input, O[] possibleOutputs, int output) {
		this.input = input;
		this.outputs = possibleOutputs;
		this.actualOutput = output;
	}

	public I getInput() {
		return input;
	}

	public O getOutput() {
		return outputs[actualOutput];
	}
	
	public int getOutputIndex(){
		return actualOutput;
	}
	
	public O[] getPossbleOutputs() {
		return outputs;
	}

	@SuppressWarnings("unchecked")
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof LabeledInstance))
			return false;

		final LabeledInstance labeledInstance = (LabeledInstance) o;
		
		if (input != null ? !input.equals(labeledInstance.input)
				: labeledInstance.input != null)
			return false;
		if(outputs.length != labeledInstance.outputs.length)
			return false;
		if(actualOutput != labeledInstance.actualOutput)
			return false;
		for(int i=0; i<outputs.length; i++){
			if(!outputs[i].equals(labeledInstance.outputs[i]))
				return false;
		}

		return true;
	}

	public int hashCode() {
		int result;
		result = (input != null ? input.hashCode() : 0);
		result = 29 * result + (input != null ? input.hashCode() : 0);
		return result;
	}
}
