package org.goobs.stats;


import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ExpectedSufficientStatistics <DOMAIN,DISTRIBUTION extends Distribution<DOMAIN>> implements SufficientStatistics <DOMAIN,DISTRIBUTION> {
	private Lock updateLock = new ReentrantLock();

	public abstract void registerDatum(DOMAIN datum, double prob);
	public abstract void clear();


	public void updateEStep(DOMAIN datum, double prob){
		updateLock.lock();
		registerDatum(datum, prob);
		updateLock.unlock();
	}
	public DISTRIBUTION runMStep(){
		updateLock.lock();
		DISTRIBUTION rtn = distribution();
		clear();
		updateLock.unlock();
		return rtn;
	}
}
