package org.goobs.stats;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class MLEPrior<DOMAIN,DISTRIBUTION extends Distribution<DOMAIN>> implements Prior<DOMAIN,DISTRIBUTION> {
	@Override
	public DISTRIBUTION posterior(DISTRIBUTION empirical) {
		return empirical;
	}
}
