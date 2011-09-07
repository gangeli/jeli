package org.goobs.stats;


public interface BayesianDistribution <DOMAIN,ME extends Distribution<DOMAIN>,PRIOR extends Prior<DOMAIN,ME>> extends Distribution<DOMAIN> {
	public ExpectedSufficientStatistics<DOMAIN,ME> newStatistics(PRIOR prior);

}
