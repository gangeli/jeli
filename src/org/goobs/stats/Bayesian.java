package org.goobs.stats;


public interface Bayesian<DOMAIN,ME extends Distribution<DOMAIN>,PRIOR extends Prior<DOMAIN,ME>>{
	public ExpectedSufficientStatistics<DOMAIN,ME> newStatistics(PRIOR prior);
}
