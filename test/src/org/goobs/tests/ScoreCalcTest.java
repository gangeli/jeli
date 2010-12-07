package org.goobs.tests;

import java.util.Random;

import org.goobs.testing.ScoreCalc;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.*;

public class ScoreCalcTest {
	double TOLERANCE = 0.0000001;
	
	/**
	 * JAVANLP gold pearsonCorrelation method
	 * @param x
	 * @param y
	 * @return
	 */
	private static double pearsonCorrelation(double[] x, double[] y) {
		double result;
		double sum_sq_x = 0, sum_sq_y = 0;
		double mean_x = x[0], mean_y = y[0];
		double sum_coproduct = 0;
		for (int i = 2; i < x.length + 1; ++i) {
			double w = (i - 1) * 1.0 / i;
			double delta_x = x[i - 1] - mean_x;
			double delta_y = y[i - 1] - mean_y;
			sum_sq_x += delta_x * delta_x * w;
			sum_sq_y += delta_y * delta_y * w;
			sum_coproduct += delta_x * delta_y * w;
			mean_x += delta_x / i;
			mean_y += delta_y / i;
		}
		double pop_sd_x = Math.sqrt(sum_sq_x / x.length);
		double pop_sd_y = Math.sqrt(sum_sq_y / y.length);
		double cov_x_y = sum_coproduct / x.length;
		double denom = pop_sd_x * pop_sd_y;
		if (denom == 0.0)
			return 0.0;
		result = cov_x_y / denom;
		return result;
	}
	
	private Random rand = new Random();
	
	@Test
	public void pearsonCorrelationTest(){
		int size = 1000;
		double[] guess = new double[size];
		double[] gold = new double[size];
		ScoreCalc<Double> toTest = new ScoreCalc<Double>();
		//--1.0 correlation
		toTest = new ScoreCalc<Double>();
		for(int i=0; i<size; i++){
			double d = rand.nextDouble();
			guess[i] = d;
			gold[i] = d;
			toTest.enterContinuous(d, d);
		}
		assertEquals(1.0, toTest.pearson(), TOLERANCE);
		assertEquals(pearsonCorrelation(guess, gold), toTest.pearson(), TOLERANCE);
		assertEquals(1.0, toTest.pearson(), TOLERANCE);
		//--Random correlation
		for(int i=0; i<100; i++){
			toTest = new ScoreCalc<Double>();
			for(int j=0; j<size; j++){
				guess[j] = rand.nextDouble();
				gold[j] = rand.nextDouble();
				toTest.enterContinuous(guess[j], gold[j]);
			}
			toTest.pearson();
			assertEquals(pearsonCorrelation(guess, gold), toTest.pearson(), TOLERANCE);
		}
	}
	
	@Test
	public void spearmanCorrelationTest(){
		//--Basic Test
		//(no repeats)
		ScoreCalc<Double> test = (new ScoreCalc<Double>()).setStreaming(false);
		double[] a = new double[]{1.0,2.0,3.0};
		double[] b = new double[]{1.0,3.0,4.0};
		for(int i=0; i<a.length; i++){
			test.enterContinuous(a[i], b[i]);
		}
		assertEquals(1.0, test.spearman(), TOLERANCE);
		//(repeats)
		test = (new ScoreCalc<Double>()).setStreaming(false);
		a = new double[]{1.0,2.0,1.0,3.0};
		b = new double[]{3.2,10.0,3.2,11.0};
		for(int i=0; i<a.length; i++){
			test.enterContinuous(a[i], b[i]);
		}
		assertEquals(1.0, test.spearman(), TOLERANCE);
		//(repeats2)
		test = (new ScoreCalc<Double>()).setStreaming(false);
		a = new double[]{1.0,2.0,2.0,2.0,2.0,2.0,3.0};
		b = new double[]{1.0,2.0,2.0,2.0,2.0,2.0,4.0};
		for(int i=0; i<a.length; i++){
			test.enterContinuous(a[i], b[i]);
		}
		assertEquals(1.0, test.spearman(), TOLERANCE);
		//(perfect negative correlation)
		test = (new ScoreCalc<Double>()).setStreaming(false);
		a = new double[]{1.0,2.0,1.0,3.0};
		b = new double[]{6.3,2.9,6.3,1.0};
		for(int i=0; i<a.length; i++){
			test.enterContinuous(a[i], b[i]);
		}
		assertEquals(-1.0, test.spearman(), TOLERANCE);
		//(random correlation)
		test = (new ScoreCalc<Double>()).setStreaming(false);
		a = new double[]{2.3,5.4,6.4,9.2};
		b = new double[]{1.5,3.0,9.2,5.7};
		for(int i=0; i<a.length; i++){
			test.enterContinuous(a[i], b[i]);
		}
		assertEquals(test.spearman(), test.spearman(), TOLERANCE);
		assertTrue(test.spearman()<=1.0);
		assertTrue(test.spearman()>=0.0);
		//--TODO better tests
	}
}
