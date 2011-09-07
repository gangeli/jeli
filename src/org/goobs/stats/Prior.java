package org.goobs.stats;

public interface Prior <DOMAIN,DIST extends Distribution<DOMAIN>> {
	public DIST posterior(DIST empirical);
}
