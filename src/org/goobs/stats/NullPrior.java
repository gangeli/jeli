package org.goobs.stats;

import java.lang.reflect.InvocationTargetException;

public class NullPrior<DOMAIN,DISTRIBUTION extends DiscreteDistribution<DOMAIN>> implements Prior<DOMAIN,DISTRIBUTION> {
	@SuppressWarnings("unchecked")
	@Override
	public DISTRIBUTION posterior(DISTRIBUTION empirical) {
		try {
			DISTRIBUTION rtn = (DISTRIBUTION) empirical.getClass().getMethod("clone").invoke(empirical);
			rtn.makeFlat();
			return rtn;
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}
}
