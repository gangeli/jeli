package org.goobs.exec;

public interface ThreadedFunction<Input,Output> {
	public Output call(Input in);		//Is not locked
	public void process(Output out);	//Is locked
}
