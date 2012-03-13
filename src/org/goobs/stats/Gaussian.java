package org.goobs.stats;

import org.goobs.util.Decodable;
import org.goobs.util.Pair;
import org.goobs.util.Utils;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 *         Large chunks taken from Apache Commons Math (http://commons.apache.org/math/)
 *         TODO make this less of a blatant copyright infringement
 */
public class Gaussian extends ContinuousDistribution implements Bayesian<Double, Gaussian, Prior<Double, Gaussian>>, Prior<Double, Gaussian>, Decodable {
	private static final double[] LANCZOS = {
			0.99999999999999709182,
			57.156235665862923517,
			-59.597960355475491248,
			14.136097974741747174,
			-0.49191381609762019978,
			.33994649984811888699e-4,
			.46523628927048575665e-4,
			-.98374475304879564677e-4,
			.15808870322491248884e-3,
			-.21026444172410488319e-3,
			.21743961811521264320e-3,
			-.16431810653676389022e-3,
			.84418223983852743293e-4,
			-.26190838401581408670e-4,
			.36899182659531622704e-5,
	};

	private static abstract class ContinuedFraction {
		protected abstract double getA(int n, double x);

		abstract double getB(int n, double x);

		public double evaluate(double x, double epsilon, int maxIterations) {
			double p0 = 1.0;
			double p1 = getA(0, x);
			double q0 = 0.0;
			double q1 = 1.0;
			double c = p1 / q1;
			int n = 0;
			double relativeError = Double.MAX_VALUE;
			while (n < maxIterations && relativeError > epsilon) {
				++n;
				double a = getA(n, x);
				double b = getB(n, x);
				double p2 = a * p1 + b * p0;
				double q2 = a * q1 + b * q0;
				boolean infinite = false;
				if (Double.isInfinite(p2) || Double.isInfinite(q2)) {
					double scaleFactor = 1d;
					double lastScaleFactor = 1d;
					final int maxPower = 5;
					final double scale = Math.max(a, b);
					if (scale <= 0) {	// Can't scale
						throw new RuntimeException("Did not converge");
					}
					infinite = true;
					for (int i = 0; i < maxPower; i++) {
						lastScaleFactor = scaleFactor;
						scaleFactor *= scale;
						if (a != 0.0 && a > b) {
							p2 = p1 / lastScaleFactor + (b / scaleFactor * p0);
							q2 = q1 / lastScaleFactor + (b / scaleFactor * q0);
						} else if (b != 0) {
							p2 = (a / scaleFactor * p1) + p0 / lastScaleFactor;
							q2 = (a / scaleFactor * q1) + q0 / lastScaleFactor;
						}
						infinite = Double.isInfinite(p2) || Double.isInfinite(q2);
						if (!infinite) {
							break;
						}
					}
				}

				if (infinite) {
					// Scaling failed
					throw new RuntimeException("Did not converge");
				}

				double r = p2 / q2;

				if (Double.isNaN(r)) {
					throw new RuntimeException("Did not converge");
				}
				relativeError = Math.abs(r / c - 1.0);

				// prepare for next iteration
				c = p2 / q2;
				p0 = p1;
				p1 = p2;
				q0 = q1;
				q1 = q2;
			}

			if (n >= maxIterations) {
				throw new RuntimeException("Max Count Exceeded");
			}

			return c;
		}
	}

	private static class GaussianESS extends ExpectedSufficientStatistics<Double, Gaussian> implements Serializable {
		private List<Pair<Double,Double>> data = new LinkedList<Pair<Double,Double>>();

		private final Prior<Double, Gaussian> prior;
		private final double fixedSigma;

		private GaussianESS(Prior<Double,Gaussian> prior){
			this(prior,-1.0);
		}
		private GaussianESS(Prior<Double,Gaussian> prior, double sigma){
			this.prior = prior;
			this.fixedSigma = sigma;
		}
		
		@Override
		protected void registerDatum(Double x, double prob) {
			data.add(Pair.make(x,prob));
		}
		@Override
		public void clear() {
			data = new LinkedList<Pair<Double,Double>>();
		}
		@Override
		public Gaussian distribution() {
			//--Mu
			double muNumer = 0.0;
			double denom = 0.0;
			for(Pair<Double,Double> datum : data){
				double x = datum.car();
				double prob = datum.cdr();
				assert prob <= 1.0 && prob >= 0.0;
				muNumer += x*prob;
				denom += prob;
			}
			double mu = denom == 0 ? 0.0 : muNumer / denom;
			//--Sigma
			double sigmasq;
			if(fixedSigma >= 0.0){
				sigmasq = fixedSigma*fixedSigma;
			} else {
				double sigmasqNumer = 0.0;
				for(Pair<Double,Double> datum : data){
					double x = datum.car();
					double prob = datum.cdr();
					sigmasqNumer += prob * (x-mu)*(x-mu);
				}
				sigmasq = denom == 0.0 ? 1.0 : sigmasqNumer / denom;
			}
			//--Apply Prior
			assert denom > 0;
			assert sigmasq >= 0.0;
			assert !Double.isInfinite(sigmasq);
			assert !Double.isNaN(muNumer) && !Double.isNaN(denom) && !Double.isNaN(sigmasq);
			return prior.posterior(new Gaussian(muNumer, denom, Math.sqrt(sigmasq)));
		}
	}


	public final double mu;
	public final double sigma;
	public final double sumXi;
	public final double n;

	private Gaussian(){
		this(0.0, 1.0);
	}

	public Gaussian(double mu, double sigma) {
		this.mu = mu;
		this.sigma = sigma;
		this.sumXi = mu;
		this.n = 1.0;
	}
	
	public Gaussian(double sumXi, double n, double sigma){
		this.sumXi = sumXi;
		this.n = n;
		this.sigma = sigma;
		this.mu = sumXi / n;
	}

	@Override
	public ExpectedSufficientStatistics<Double, Gaussian> newStatistics(Prior<Double, Gaussian> muPrior) {
		return newStatistics(muPrior, -1.0);
	}

	public ExpectedSufficientStatistics<Double, Gaussian> newStatistics(Prior<Double, Gaussian> muPrior, double fixedSigma) {
		if(fixedSigma >= 0.0){
			return new GaussianESS(muPrior, fixedSigma);
		} else {
			return new GaussianESS(muPrior);
		}
	}



	@Override
	public double prob(Double key) {
		return Math.exp(-(key - this.mu) * (key - this.mu) / (2.0 * this.sigma * this.sigma)) / (this.sigma * Math.sqrt(2.0 * Math.PI));
	}

	@Override
	public Double sample(Random r) {
		return (r.nextGaussian()*this.sigma) + this.mu;
	}

	/**
	 * From http://en.wikipedia.org/wiki/Conjugate_prior
	 */
	@Override
	public Gaussian posterior(Gaussian empirical) {
		//(variables used)
		double mu0 = this.mu;
		double sigmasq0 = this.sigma*this.sigma;
		double sigmasq = empirical.sigma*empirical.sigma;
		if(sigmasq == 0.0){ sigmasq = 1e-10; }
		double sumX = empirical.sumXi;
		double n = empirical.n;
		//(math goes here -- prior for normal with known variance)
		double muPosteriorMu = ((mu0 / sigmasq0) + (sumX / sigmasq)) / ( (1.0 / sigmasq0) + (n / sigmasq) );
		double muPosteriorSigmaSq = 1.0 / ( (1.0 / sigmasq0) + (n / sigmasq) );
		double muPosteriorMLE = muPosteriorMu;
		//(return)
		return new Gaussian(muPosteriorMLE,empirical.sigma);
	}

	@Override
	public String toString(KeyPrinter<Double> p) {
		return "N(" + p.format(mu) + "," + p.format(sigma) + ")";
	}

	@Override
	public double cdf(double x) {
		double rtn;
		if(this.sigma > 0.0){
			final double dev = x - this.mu;
			if (Math.abs(dev) > 40 * this.sigma) {
				return dev < 0 ? 0.0d : 1.0d;
			}
			rtn = 0.5 * (1 + erf(dev / (this.sigma * Math.sqrt(2.0))));
		} else {
			if(x > this.mu){
				rtn = 1.0;
			} else {
				rtn = 0.0;
			}
		}
		assert !Double.isNaN(rtn);
		assert rtn >= 0.0 && rtn <= 1.0;
		return rtn;
	}

	@Override
	public Decodable decode(String encoded, Type[] typeParams) {
		if(encoded.startsWith("N")){
			encoded = encoded.substring(1);
		}
		String[] cand = Utils.decodeArray(encoded);
		return new Gaussian(Double.parseDouble(cand[0]), Double.parseDouble(cand[1]));
	}

	@Override
	public String encode() {
		return "N("+mu+","+sigma+")";
	}
	
	@Override
	public String toString(){
		return encode();
	}
	
	@Override
	public int hashCode(){
		return ((int) mu*1000) ^ (((int) sigma*1000) << 7);
	}
	
	@Override
	public boolean equals(Object o){
		if(o instanceof Gaussian){
			Gaussian g = (Gaussian) o;
			return this.mu == g.mu && this.sigma == g.sigma;
		} else {
			return false;
		}
	}



	private static double logGamma(double x) {
		double ret;
		if (Double.isNaN(x) || (x <= 0.0)) {
			ret = Double.NaN;
		} else {
			double g = 607.0 / 128.0;
			double sum = 0.0;
			for (int i = LANCZOS.length - 1; i > 0; --i) {
				sum = sum + (LANCZOS[i] / (x + i));
			}
			sum = sum + LANCZOS[0];
			double tmp = x + g + .5;
			ret = ((x + .5) * Math.log(tmp)) - tmp +
					(0.5 * Math.log(2.0 * Math.PI)) + Math.log(sum / x);
		}

		return ret;
	}


	private static double regularizedGammaQ(final double a,
																					double x,
																					double epsilon,
																					int maxIterations) {
		double ret;
		if (Double.isNaN(a) || Double.isNaN(x) || (a <= 0.0) || (x < 0.0)) {
			ret = Double.NaN;
		} else if (x == 0.0) {
			ret = 1.0;
		} else if (x < a + 1.0) {
			// use regularizedGammaP because it should converge faster in this
			// case.
			ret = 1.0 - regularizedGammaP(a, x, epsilon, maxIterations);
		} else {
			// create continued fraction
			ContinuedFraction cf = new ContinuedFraction() {
				@Override
				protected double getA(int n, double x) {
					return ((2.0 * n) + 1.0) - a + x;
				}
				@Override
				protected double getB(int n, double x) {
					return n * (a - n);
				}
			};
			ret = 1.0 / cf.evaluate(x, epsilon, maxIterations);
			ret = Math.exp(-x + (a * Math.log(x)) - logGamma(a)) * ret;
		}
		return ret;
	}

	private static double regularizedGammaP(double a,
																					double x,
																					double epsilon,
																					int maxIterations) {
		double ret;
		if (Double.isNaN(a) || Double.isNaN(x) || (a <= 0.0) || (x < 0.0)) {
			ret = Double.NaN;
		} else if (x == 0.0) {
			ret = 0.0;
		} else if (x >= a + 1) {
			// use regularizedGammaQ because it should converge faster in this
			// case.
			ret = 1.0 - regularizedGammaQ(a, x, epsilon, maxIterations);
		} else {
			// calculate series
			double n = 0.0; // current element index
			double an = 1.0 / a; // n-th element in the series
			double sum = an; // partial sum
			while (Math.abs(an / sum) > epsilon &&
					n < maxIterations &&
					sum < Double.POSITIVE_INFINITY) {
				// compute next element in the series
				n = n + 1.0;
				an = an * (x / (a + n));

				// update partial sum
				sum = sum + an;
			}
			if (n >= maxIterations) {
				throw new IllegalStateException("Max count exceeded: " + maxIterations);
			} else if (Double.isInfinite(sum)) {
				ret = 1.0;
			} else {
				ret = Math.exp(-x + (a * Math.log(x)) - logGamma(a)) * sum;
			}
		}
		return ret;
	}

	private static double erf(double x) {
		if (Math.abs(x) > 40) {
			return x > 0 ? 1 : -1;
		}
		final double ret = regularizedGammaP(0.5, x * x, 1.0e-15, 10000);
		return x < 0 ? -ret : ret;
	}
}
