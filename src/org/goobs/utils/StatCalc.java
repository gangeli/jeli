package org.goobs.utils;

import java.util.HashMap;
import java.util.Map;

public class StatCalc {

	private class Counter{
		private Map<Double,Double> map = new HashMap<Double,Double>();
		private void incrementCount(double var, double incr){
			Double count = map.get(var);
			if(count == null){ count = 0.0; }
			count += incr;
			map.put(var,count);
		}
		private double argMax(){
			double max = Double.NEGATIVE_INFINITY;
			double argmax = Double.NaN;
			for(Double var : map.keySet()){
				if(map.get(var) > max){
					max = map.get(var);
					argmax = var;
				}
			}
			return argmax;
		}
		private double getCount(double var){
			Double count = map.get(var);
			if(count == null){ return 0.0; }
			return count;
		}
	}
	
	private int count; // Number of numbers that have been entered.
	private double sum; // The sum of all the items that have been entered.
	private double squareSum; // The sum of the squares of all the items.
	private double min = Double.POSITIVE_INFINITY;
	private double max = Double.NEGATIVE_INFINITY;	
	
	private Counter modeCounter = null;

	public StatCalc trackMode(){
		setTrackMode(true);
		return this;
	}

	public void setTrackMode(boolean trackMode){
		if(trackMode){
			this.modeCounter = new Counter();
		}else{
			this.modeCounter = null;
		}
	}
	
	public void add(double num){
		enter(num);
	}

	public void enter(double num) {
		// Add the number to the dataset.
		count++;
		sum += num;
		squareSum += num * num;
		if(num < min){
			min = num;
		}
		if(num > max){
			max = num;
		}
		// mode
		if(modeCounter != null){
			modeCounter.incrementCount(num, 1.0);
		}
	}

	public void enter(int num){
		enter( (double) num );
	}

	public int getCount() {
		// Return number of items that have been entered.
		return count;
	}
    public int count(){ return getCount(); }

	public double getSum() {
		// Return the sum of all the items that have been entered.
		return sum;
	}
    public double sum(){ return getSum(); }

	public int getSumInt(){
		return (int) sum;
	}

	public double getMean() {
		// Return average of all the items that have been entered.
		// Value is Double.NaN if count == 0.
		return sum / count;
	}
    public double mean(){ return getMean(); }

	public int getMeanInt(){
		return (int) (sum / count);
	}
	
	public double getMin(){
		return min;
	}
    public double min(){ return getMin(); }

	public int getMinInt(){
		return (int) min;
	}
	
	public double getMax(){
		return max;
	}
    public double max(){ return getMax(); }

	public int getMaxInt(){
		return (int) max;
	}

	public double getMode(){
		if(this.modeCounter == null) throw new IllegalStateException("Must tell StatCalc to track the mode");
		if(this.count == 0) throw new IllegalStateException("No terms in statcalc to get the mode from");
		return this.modeCounter.argMax();
	}
    public double mode(){ return getMode(); }
	public double getModeCount(){
		if(this.modeCounter == null) throw new IllegalStateException("Must tell StatCalc to track the mode");
		if(this.count == 0) throw new IllegalStateException("No terms in statcalc to get the mode from");
		return this.modeCounter.getCount(modeCounter.argMax());
	}
	

	public double getStandardDeviation() {
		// Return standard deviation of all the items that have been
		// entered.
		// Value will be Double.NaN if count == 0.
		double mean = getMean();
		return Math.sqrt(squareSum / count - mean * mean);
	}

	public double getStdev(){ return getStandardDeviation(); }
    public double stdev(){ return getStandardDeviation(); }

} // end of class StatCalc
