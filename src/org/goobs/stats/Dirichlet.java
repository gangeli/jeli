package org.goobs.stats;

import org.goobs.utils.Decodable;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Set;

public class Dirichlet<DOMAIN> implements Prior<DOMAIN,Multinomial<DOMAIN>>, Decodable {
	private CountStore<DOMAIN> counts;

	private Dirichlet(){}
	public Dirichlet(CountStore<DOMAIN> alpha){
		this.counts = alpha;
	}

	@Override
	public Multinomial<DOMAIN> posterior(Multinomial<DOMAIN> empirical) {
		try {
			Multinomial<DOMAIN> rtn = empirical.clone();
			for(DOMAIN key : empirical){
				rtn.incrementCount(key, this.counts.getCount(key));
			}
			return rtn;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Decodable decode(String encoded, Type[] typeParams) {
		try{
			double param = Double.parseDouble(encoded);
			this.counts = symmetricStore(param);
			return this;
		} catch(NumberFormatException e){
			throw new RuntimeException("DECODING NON-SYMMETRIC PRIOR NOT IMPLEMENTED");
		}
	}

	@Override
	public String encode() {
		throw new RuntimeException("NOT IMPLEMENTED");
	}

	@Override
	public String toString(){
		//--Get Info
		//(variables)
		int keyCount = 0;
		double lastKeyValue = Double.NaN;
		boolean keysSame = true;
		//(loop)
		for(DOMAIN key : counts){
			if(Double.isNaN(lastKeyValue)){
				lastKeyValue = counts.getCount(key);
			}
			if(counts.getCount(key) != lastKeyValue){
				keysSame = false;
			}
			keyCount += 1;
		}
		//--Print
		if(keyCount == 0){
			//(case: empty)
			return "Dirichlet()";
		} else if(keysSame){
			//(case: same)
			return "Dirichlet( "+lastKeyValue+" )";
		} else if(keyCount < 5){
			//(case: reasonable domain)
			StringBuilder b = new StringBuilder();
			b.append("Dirichlet( ");
			for(DOMAIN key : counts){
				b.append(key).append(":").append(counts.getCount(key));
			}
			b.append(")");
			return b.toString();
		}else{
			//(case: not reasonably sized)
			return "Dirichlet( ... )";
		}
	}

	public static <D> Dirichlet<D> ZERO(){
		return new Dirichlet<D>(CountStores.<D>MAP());
	}

	private static <D> CountStore<D> symmetricStore(final double count){
		return new CountStore<D>() {
			@Override public double getCount(D key) {return count; }
			@Override public void setCount(D key, double count) { throw new RuntimeException("NOT IMPLEMENTED");	}
			@Override public CountStore<D> emptyCopy() { return symmetricStore(0);	}
			@SuppressWarnings({"CloneDoesntCallSuperClone"})
			@Override public CountStore<D> clone() throws CloneNotSupportedException { return this; }
			@Override public CountStore<D> clear() { throw new RuntimeException("NOT IMPLEMENTED"); }
			@Override public Iterator<D> iterator() {
				return new Iterator<D>(){
					boolean returned = false;
					@Override
					public boolean hasNext() {
						return !returned;
					}
					@Override
					public D next() {
						returned = true;
						return null;
					}
					@Override
					public void remove() {
						throw new RuntimeException("NOT IMPLEMENTED");
					}
				};
			}
		};
	}

	@SuppressWarnings({"unchecked"})
	public static <D> Dirichlet<D> SYMMETRIC(final double count){
		return new Dirichlet<D>((CountStore<D>) symmetricStore(count));
	}

	public static <D> Dirichlet<D> SYMMETRIC(Set<D> domain, double count){
		CountStore<D> counts = CountStores.MAP();
		for(D key : domain){
			counts.setCount(key, count);
		}
		return new Dirichlet<D>(counts);
	}
	public static Dirichlet<Integer> SYMMETRIC(int domainSize, double count){
		CountStore<Integer> counts = CountStores.ARRAY(domainSize);
		for(int i=0; i<domainSize; i++){
			counts.setCount(i,count);
		}
		return new Dirichlet<Integer>(counts);
	}
}
