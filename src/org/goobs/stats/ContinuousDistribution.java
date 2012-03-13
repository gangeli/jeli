package org.goobs.stats;

import java.util.Random;

public abstract class ContinuousDistribution implements Distribution<Double> {

	public abstract double cdf(double val);
	
	public double cdf(double begin, double end){
		return cdf(end) - cdf(begin);
	}

	@Override
	public Double sample(Random r) {
		//--Variables
		double target = r.nextDouble();
		double step = 1.0;
		double cand = 0.0;
		double initialCDF = this.cdf(cand);
		int lastHopDir = initialCDF < target ? 1 : -1;
		//--Find Bounds
		if(lastHopDir == 1){
			while(this.cdf(cand) < target){
				step *= 2;
				cand += step;
			}
		} else {
			while(this.cdf(cand) > target){
				step *= 2;
				cand -= step;
			}
		}
		//--Search
		double point = this.cdf(cand);
		while(Math.abs(point-target) > 1e-05){
			if(point >= target && lastHopDir == 1){
				//(case: overshot to the right)
				step /= 2;
				lastHopDir = -1;
				cand -= step;
			} else if(point >= target && lastHopDir == -1){
				//(case: undershot from the right)
				cand -= step;
			} else if(point < target && lastHopDir == 1){
				//(case: undershot from the left)
				cand += step;
			} else if(point < target && lastHopDir == -1){
				//(case: overshot to the left)
				step /= 2;
				lastHopDir = 1;
				cand += step;
			}
			point = this.cdf(cand);
		}
		//--Return
		return cand;
	}
}
