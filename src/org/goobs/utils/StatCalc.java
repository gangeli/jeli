package org.goobs.utils;
import org.goobs.foreign.Counter;

public class StatCalc {

	private int count; // Number of numbers that have been entered.
	private double sum; // The sum of all the items that have been entered.
	private double squareSum; // The sum of the squares of all the items.
	private double min = Double.POSITIVE_INFINITY;
	private double max = Double.NEGATIVE_INFINITY;

	private Counter<Double> modeCounter = null;

	public StatCalc trackMode(){
		setTrackMode(true);
		return this;
	}

	public void setTrackMode(boolean trackMode){
		if(trackMode){
			this.modeCounter = new Counter<Double>();
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

	public double getSum() {
		// Return the sum of all the items that have been entered.
		return sum;
	}

	public int getSumInt(){
		return (int) sum;
	}

	public double getMean() {
		// Return average of all the items that have been entered.
		// Value is Double.NaN if count == 0.
		return sum / count;
	}

	public int getMeanInt(){
		return (int) (sum / count);
	}
	
	public double getMin(){
		return min;
	}

	public int getMinInt(){
		return (int) min;
	}
	
	public double getMax(){
		return max;
	}

	public int getMaxInt(){
		return (int) max;
	}

	public double getMode(){
		if(this.modeCounter == null) throw new IllegalStateException("Must tell StatCalc to track the mode");
		if(this.count == 0) throw new IllegalStateException("No terms in statcalc to get the mode from");
		return this.modeCounter.argMax();
	}
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

} // end of class StatCalc
