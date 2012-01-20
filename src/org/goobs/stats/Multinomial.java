package org.goobs.stats;


import java.text.DecimalFormat;
import java.util.*;

public class Multinomial <DOMAIN> implements Bayesian<DOMAIN,Multinomial<DOMAIN>,Prior<DOMAIN,Multinomial<DOMAIN>>>, DiscreteDistribution<DOMAIN> {
	/*
		VARIABLES
	 */
	// -- STATIC --
	private static DecimalFormat df = new DecimalFormat("0.000");
	// -- NON-STATIC --
	private final CountStore<DOMAIN> counts;
	private double totalCount = 0.0;

	/*
		METHODS
	 */
	public Multinomial(CountStore<DOMAIN> countStore){
		this.counts = countStore;
	}

	
	private boolean sumsTo1(){
		double sum = 0;
		for(DOMAIN key : this){
			sum += this.counts.getCount(key);
		}
		return Math.abs(sum - 1.0) < 0.00001;
	}

	private boolean verifyTotal(){
		double sum = 0;
		for(DOMAIN key : this){
			sum += this.counts.getCount(key);
		}
		double totalProb = 0;
		for(DOMAIN key : this){
			totalProb += this.prob(key);
		}
		return Math.abs(sum - totalCount) < 0.00001 && Math.abs(totalProb - 1.0) < 0.00001;
	}
	
	public double getCount(DOMAIN key){ return counts.getCount(key); }

	public void setCount(DOMAIN key, double value){
		double lastCount = counts.getCount(key);
		totalCount += value - lastCount;
		counts.setCount(key, value);
	}

	public void incrementCount(DOMAIN key, double value){
		setCount(key, counts.getCount(key) + value);
	}
	
	public void normalize(){
		assert verifyTotal();
		for(DOMAIN key : this){
			this.counts.setCount(key, this.getCount(key) / totalCount);
		}
		assert sumsTo1();
		this.totalCount = 1.0;
	}

	public Multinomial<DOMAIN> initUniform(){
		for(DOMAIN key : counts){
			setCount(key, 1.0);
		}
		return this;
	}

	public Multinomial<DOMAIN> initRandom(){
		Random r = new Random();
		for(DOMAIN key : counts){
			setCount(key, r.nextDouble());
		}
		return this;
	}

	@SuppressWarnings({"CloneDoesntCallSuperClone"})
	public Multinomial<DOMAIN> clone() throws CloneNotSupportedException {
		Multinomial<DOMAIN> rtn = new Multinomial<DOMAIN>(this.counts.clone());
		rtn.totalCount = this.totalCount;
		return rtn;
	}

	public void clear(){
		for(DOMAIN key : counts){
			counts.setCount(key,0.0);
		}
		totalCount = 0.0;
	}

	public List<DOMAIN> zeroes(){
		List<DOMAIN> rtn = new LinkedList<DOMAIN>();
		for(DOMAIN key : counts){
			if(prob(key) == 0.0){
				rtn.add(key);
			}
		}
		return rtn;
	}


	/*
		OVERRIDDEN METHODS
	 */
	@Override
	public double prob(DOMAIN key){
		return counts.getCount(key) / totalCount;
	}


	@Override
	public ExpectedSufficientStatistics<DOMAIN, Multinomial<DOMAIN>> newStatistics(Prior<DOMAIN, Multinomial<DOMAIN>> prior) {
		return new MultinomialSufficientStatistics<DOMAIN,Prior<DOMAIN,Multinomial<DOMAIN>>>(prior,this.counts.emptyCopy());
	}

	@Override
	public void makeFlat() {
		this.clear();
	}

	@Override
	public Iterator<DOMAIN> iterator() {
		return counts.iterator();
	}

	@Override public String toString(KeyPrinter<DOMAIN> printer){
		//--Sort Counts
		//(create priority queue)
		PriorityQueue<DOMAIN> pq = new PriorityQueue<DOMAIN>(10,new Comparator<DOMAIN>(){
			@Override public int compare(DOMAIN a, DOMAIN b) {
				double cntA = getCount(a);
				double cntB = getCount(b);
				if(cntA > cntB){ return -1; }
				else if(cntA < cntB){ return 1; }
				else { return 0; }
			}
		});
		//(populate queue)
		for(DOMAIN key : this){
			pq.add(key);
		}
		//--Create String
		//(header)
		StringBuilder b = new StringBuilder();
		b.append("Mult[ ");
		//(top scores)
		for(int i=0; i<3; i++){
			if(!pq.isEmpty()){
				DOMAIN key = pq.poll();
				b.append(printer.format(key)).append(":").append(df.format(prob(key))).append(" ");
			}
		}
		//(middle)
		int skipped = 0;
		while(pq.size() > 3){ pq.poll(); skipped += 1; }
		if(!pq.isEmpty()){ b.append("...(").append(skipped).append(")... "); }
		//(bottom scores)
		while(!pq.isEmpty()){
			DOMAIN key = pq.poll();
			b.append(printer.format(key)).append(":").append(df.format(prob(key))).append(" ");
		}
		//(footer)
		b.append("]");
		//--Return
		return b.toString();
	}

	@Override public String toString(){
		return toString(new KeyPrinter<DOMAIN>() {
			@Override public String format(DOMAIN d) { return d.toString(); }
		});
	}

	@Override public boolean equals(Object o){
		if(o instanceof Multinomial){
			try{
				@SuppressWarnings({"unchecked"}) Multinomial<DOMAIN> other = (Multinomial<DOMAIN>) o;
				//(total count)
				if(other.totalCount != this.totalCount){ return false; }
				//(everthing in this is in other)
				for(DOMAIN key : this){
					if(this.getCount(key) != other.getCount(key)){ return false; }
				}
				//(everything in other is in this)
				for(DOMAIN key : other){
					if(other.getCount(key) != this.getCount(key)){ return false; }
				}
				//(return ok)
				return true;
			} catch(ClassCastException e){
				return false;
			}
		} else {
			return false;
		}
	}

	@Override public int hashCode(){
		return (int) (totalCount * 10000.0);
	}


	/*
			LEARNING OPTIONS
	*/
	private static class MultinomialSufficientStatistics<D,P extends Prior<D,Multinomial<D>>> extends ExpectedSufficientStatistics<D,Multinomial<D>> {
		private final P prior;
		private final Multinomial<D> posterior;
		private MultinomialSufficientStatistics(P prior, CountStore<D> store){
			this.prior = prior;
			this.posterior = new Multinomial<D>(store);
		}
		@Override
		public void registerDatum(D datum, double prob) {
			this.posterior.incrementCount(datum, prob);
		}
		@Override
		public void clear() {
			posterior.clear();
		}

		@Override
		public String toString(){
			return "ESS: " + posterior.toString() + " of " + posterior.totalCount + " with prior " + prior;
		}

		public Iterable<D> domain() {
			return new Iterable<D>(){
				@Override
				public Iterator<D> iterator() {
					final Iterator<D> iter = posterior.iterator();
					return new Iterator<D>() {
						private D next = null;
						@Override
						public boolean hasNext() {
							if(next() != null){
								return true;
							} else {
								while(iter.hasNext() && next != null){
									D cand = iter.next();
									if(posterior.getCount(next()) > 0){
										next = cand;
									}
								}
								return next() != null;
							}
						}
						@Override
						public D next() {
							if(!hasNext()){ throw new NoSuchElementException("Iterator is empty"); }
							return next;
						}
						@Override
						public void remove() {
							iter.remove();
						}
					};
				}
			};
		}

		@Override
		public Multinomial<D> distribution() {
			if(posterior.totalCount == 0.0){
				try {
					return this.prior.posterior(posterior.clone().initUniform());
				} catch (CloneNotSupportedException e) {
					throw new RuntimeException(e);
				}
			} else {
				return this.prior.posterior(posterior);
			}
		}

		@Override public boolean equals(Object o){
			if(o instanceof MultinomialSufficientStatistics){
				try{
					@SuppressWarnings({"unchecked"}) MultinomialSufficientStatistics<D,P> other = (MultinomialSufficientStatistics<D,P>) o;
					return other.prior.equals(prior) && other.posterior.equals(posterior);
				} catch(ClassCastException e){
					return false;
				}
			} else {
				return false;
			}
		}
		@Override public int hashCode(){ return prior.hashCode() ^ posterior.hashCode(); }
	}

}
