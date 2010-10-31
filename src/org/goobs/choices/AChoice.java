package org.goobs.choices;

public interface AChoice <Input, Output> {
	
	/**
	 * The term marking that this markov chain is done.
	 * Note that this term is checked for strict equality ('==')
	 * @return the stop term
	 */
	public Output stopTerm();
	
	/**
	 * The input corresponding to no choices being made
	 * @return the null history term
	 */
	public Input noHistory();
	
	/**
	 * The output for a particular input, in conjunction with the entire history
	 * @param in the input to the choice
	 * @return
	 */
	public Output output(Input in);
	
	
	/**
	 * Given that we just generated an output,
	 * what is the new history?
	 * @param out output just generated
	 * @return the new input item (new history)
	 */
	public Input feedback(Input in, Output out);
	
	/**
	 * Converts an output to its string representation
	 * In most cases, this can simply be Output.toString
	 * @param in input to the given choice
	 * @param out output to convert to a string
	 *
	 */
	public String outputToString(Input in, Output out);
}
