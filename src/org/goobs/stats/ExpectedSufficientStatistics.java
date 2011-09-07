package org.goobs.stats;


public abstract class ExpectedSufficientStatistics <DOMAIN,DISTRIBUTION extends Distribution<DOMAIN>> implements SufficientStatistics <DOMAIN,DISTRIBUTION> {
	public abstract void registerDatum(DOMAIN datum, double prob);
	public abstract void clear();

	public void updateEStep(DOMAIN datum, double prob){ registerDatum(datum, prob); }
	public DISTRIBUTION runMStep(){
		DISTRIBUTION rtn = distribution();
		clear();
		return rtn;
	}
}
