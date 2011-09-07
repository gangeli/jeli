package org.goobs.stats;


public interface SufficientStatistics <DOMAIN,DISTRIBUTION extends Distribution<DOMAIN>> {
	public DISTRIBUTION distribution();
}
