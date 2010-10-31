package org.goobs.testing;

import static org.goobs.exec.Log.*;

public class ScoreCalc <T> {

	private int exCount;
	
	private int guessCorrect;
	private int guessTotal;
	private int goldCorrect;
	private int goldTotal;
	
	public ScoreCalc(){
		
	}
	
	public void enterDiscrete(T guess, T gold){
		if(guess == gold){
			guessCorrect += 1;
			goldCorrect += 1;
		}
		guessTotal += 1;
		goldTotal += 1;
		exCount += 1;
	}
	
	public void enterUnordered(T[] guess, T[] gold){
		//TODO
		throw new IllegalStateException("Not implemented!");
	}
	public void enterOrdered(T[] guess, T[] gold){
		//TODO
		throw new IllegalStateException("Not implemented!");
	}
	
	
	public double precision(){
		return ((double) guessCorrect) / ((double) guessTotal);
	}
	public double recall(){
		return ((double) goldCorrect) / ((double) goldTotal);
	}
	public double FMeasure(double B){
		return (1.0+B) * (precision() * recall()) / (B*B*precision() + recall());
	}
	public double F1(){ return FMeasure(1.0); }
	public double F2(){ return FMeasure(2.0); }
	public double FHalf(){ return FMeasure(0.5); }
	
	
	public void printDiscrete(){
		log("Result", "F1: " + F1(), true);
		log("Result", "Precision: " + precision(), true);
		log("Result", "Recall: " + recall(), true);
	}
}
