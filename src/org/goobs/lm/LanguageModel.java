package org.goobs.lm;

import java.util.HashSet;
import java.util.LinkedList;

import org.goobs.distributions.DiscreteDistribution;
import org.goobs.distributions.Distribution;
import org.goobs.distributions.ProbHash;
import org.goobs.distributions.ProbVec;
import org.goobs.exec.Log;
import org.goobs.foreign.Indexer;
import org.goobs.utils.Pair;


public abstract class LanguageModel {
	
	private static final DiscreteDistribution<Integer> EMPTY_VEC = new ProbVec(0);
	
	protected final class Tuple extends Pair<Integer,Tuple>{
		private static final long serialVersionUID = 588084571720246192L;
		private Tuple(Integer i, Tuple t){
			super(i, t);
		}
		protected int size(){
			if(cdr() == null){
				return 1;
			}else{
				return 1 + cdr().size();
			}
		}
		private Tuple reverseHelper(Tuple rest){
			if(cdr() == null){
				return new Tuple(car(), rest);
			}else{
				return cdr().reverseHelper(new Tuple(car(), rest));
			}
		}
		protected Tuple reverse(){
			return reverseHelper(null);
		}
		protected String prettyString(){
			if(cdr() == null){
				return wstr(car());
			}else{
				return wstr(car()) + " " + cdr().prettyString();
			}
		}
	}
	
	private Indexer<String> wordIndexer = new Indexer<String>();
	private Indexer<String> numIndexer = new Indexer<String>();
	
	private int UNK = wordIndexer.addAndGetIndex("*UNK*");
	private int START = wordIndexer.addAndGetIndex("*START*");
	private int STOP = wordIndexer.addAndGetIndex("*STOP*");
	private int NUM = wordIndexer.addAndGetIndex("*INT*");
	protected int N(){ return wordIndexer.size(); }
	private double NUM_count(){ return (double) numIndexer.size(); }
	protected String wstr(int x){ return wordIndexer.get(x); }
	
	private LinkedList<int[]> examples = new LinkedList<int[]>(); 
	private DiscreteDistribution<Integer>[][] grams;
	protected DiscreteDistribution<Integer> uniqueContexts;
	private HashSet<Tuple> seenSequences = new HashSet<Tuple>();
	private Indexer<Tuple>[] tupleIndexers;
	private int numTuples(int n){ return tupleIndexers[n-1].size(); }
	
	private int depth;

	private boolean trained = false;
	
	
	protected int unkNum = 1;
	                                                     
	@SuppressWarnings("unchecked")
	public LanguageModel(int depth){
		//--Set Variables
		this.depth = depth;
		this.grams = new DiscreteDistribution[depth][];
		this.tupleIndexers = new Indexer[depth];
		this.uniqueContexts = new ProbHash<Integer>();
		//--Set Up Indexers
		for(int n=0; n<depth; n++){
			tupleIndexers[n] = new Indexer<Tuple>();
		}
	}
	
	private boolean isUnk(String word){
		return !wordIndexer.contains(word);
	}
	
	private boolean isInt(String word){
		char[] chars = word.toCharArray();
		if(chars.length == 0) return false;	//degenerated case
		int start = chars[0] == '-' ? 1 : 0;
		for(int i=start; i<chars.length; i++){
			if(chars[i] < '0' || chars[i] > '9'){
				return false;
			}
		}
		return true;
	}
	
	public void addSentence(String[] sentence){
		int[] sent = new int[sentence.length];
		for(int i=0; i<sent.length; i++){
			String word = sentence[i].trim();
			if(isInt(word)) { numIndexer.addAndGetIndex(word); sent[i] = NUM; }
			else { sent[i] = wordIndexer.addAndGetIndex(word); }
		}
		this.examples.add(sent);
	}
	
	private void updateNGram(int[] ex, int n, boolean doUpdate){
		Indexer<Tuple> indexer = tupleIndexers[n-1];
		Distribution<Integer>[] gram = grams[n-1];
		
		//--Count grams up until stop
		for(int pos=0; pos<ex.length+1; pos++){
			//(create the tuple key)
			Tuple tuple = null;
			int start = pos-(n-1);
			for(int i=start; i<pos; i++){
				if(i < 0){
					tuple = new Tuple(START, tuple);
				} else {
					tuple = new Tuple(ex[i], tuple);
				}
			}
			if(tuple != null) tuple = tuple.reverse();
			//(increment the count for that tuple)
			int tupInt = indexer.addAndGetIndex(tuple);
			if(doUpdate){
				int word = pos < ex.length ? ex[pos] : STOP;
				//(update the unique contexts if applicable)
				if(n == 2){ //only care about single previous word
					Tuple completeSequence = new Tuple(word, tuple.reverse()).reverse();
					//(update the unique contexts if applicable)
					if(!seenSequences.contains(completeSequence)){
						uniqueContexts.addCount(word, 1.0);
						seenSequences.add(completeSequence);
					}
				}
				//(update the count)
				gram[tupInt].addCount(word, 1.0);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void train(){
		trained = true;
		//--Prepare to Count
		//(initial count)
		for(int[] ex : examples){
			for(int n=0; n<depth; n++){
				updateNGram(ex, n+1, false);
			}
		}
		//(set up variables)
		Log.debug("Language Model Stats:");
		Log.debug("   # of words: " + N());
		for(int n=0; n<depth; n++){
			grams[n] = new DiscreteDistribution[numTuples(n+1)];
			Log.debug(null,"   " + (n+1) + "-gram tails: " + numTuples(n+1),true);
			for(int i=0; i<numTuples(n+1); i++){
				grams[n][i] = new ProbHash<Integer>();
			}
		}
		
		//--Count the Grams
		//(count some UNKs)
		grams[0][tupleIndexers[0].addAndGetIndex(null)].addCount(UNK, unkNum);
		uniqueContexts.addCount(UNK, unkNum);
		uniqueContexts.addCount(NUM, 1);
		//(count training data)
		for(int[] ex : examples){
			for(int n=0; n<depth; n++){
				updateNGram(ex, n+1, true);
			}
		}
		
		//--Error Check
		for(int w=0; w<N(); w++){
			if(w != START && uniqueContexts.getProb(w) == 0.0){
				throw Log.internal("No unique contexts for observed word " + w + " => " + wstr(w));
			}
		}
	}
	
	protected double getNGramCount(int word, Tuple tail){
		int gInt = tupleIndexers[tail.size()].indexOf(tail);
		return grams[tail.size()][gInt].getCount(word);
	}
	
	protected double getNGramProb(int word, Tuple tail){
		int gInt = tupleIndexers[tail.size()].indexOf(tail);
		double prob = grams[tail.size()][gInt].getProb(word);
		return (word == NUM) ? prob / NUM_count() : prob;
	}
	
	private int tailToInt(Tuple tail){
		if(tupleIndexers[tail.size()].contains(tail)){
			return tupleIndexers[tail.size()].indexOf(tail);
		}else{
			return -1;
		}
	}
	
	protected DiscreteDistribution<Integer> getDistribution(Tuple tail){
		int tailInt = tailToInt(tail);
		if(tailInt < 0){
			return EMPTY_VEC;
		} else {
			return grams[tail.size()][tailInt];
		}
	}
	
	//-----
	// LIKELY TO OVERWRITE
	//-----
	
	protected abstract double getCondProb(int word, Tuple tail);
	
	//-----
	// TOP LEVEL METHODS
	//-----
	
	private int toLocalWord(String word){
		if(isInt(word)) return NUM;
		else if(isUnk(word)) return UNK;
		else return wordIndexer.indexOf(word);
	}
	
	private Tuple toTail(String[] history){
		//(get the start)
		int histStart = Math.max(0, history.length-depth+1);
		//(create the tail)
		int len=0;
		Tuple start = null;
		Tuple current = null;
		if(history.length > 0){
			start = new Tuple(toLocalWord(history[histStart]), null);
			current = start;
			len += 1;
			for(int i=histStart+1; i<history.length; i++){
				current.setCdr(new Tuple(toLocalWord(history[i]), null));
				current = current.cdr();
				len += 1;
			}
		}
		//(add *START*'s as necessary)
		while(len < depth-1){
			start = new Tuple(START, start);
			len += 1;
		}
		return start;
	}

	@SuppressWarnings("unchecked")
	public void addUnigram(String word, double count, double uniqueContexts){
		//(overhead)
		if(trained) throw Log.fail("Cannot add unigrams after the language model has been trained");
		if(grams[0] == null) grams[0] = new DiscreteDistribution[1]; //unigram only ever has one tail
		if(grams[0][0] == null) grams[0][0] = new ProbHash<Integer>();
		//(get distribution)
		DiscreteDistribution<Integer> dist = grams[0][tupleIndexers[0].addAndGetIndex(null)];
		//(add count)
		int w = wordIndexer.addAndGetIndex(word);
		this.uniqueContexts.addCount(w, uniqueContexts);
		dist.addCount(w, count);
	}

	public boolean containsWord(String word){
		if(isInt(word)) return true;
		else if(isUnk(word)) return false;
		else return true;
	}
	
	public double nextWordProb(String[] history, String word){
		Tuple tail = toTail(history);
		int w = toLocalWord(word);
		return getCondProb(w,tail);
	}
	
	public double stopProb(String[] history){
		Tuple tail = toTail(history);
		int w = STOP;
		return getCondProb(w,tail);
	}
	
	public Distribution<String> nextWordDist(String[] history){
		ProbHash<String> rtn = new ProbHash<String>();
		Tuple tail = toTail(history);
		for(int i=0; i<N(); i++){
			if(i != START){
				rtn.addCount(wordIndexer.get(i), getCondProb(i, tail));
			}
		}
		return rtn;
	}
	
	public String[] generate(){
		String[] soFar = new String[0];
		while(true){
			String gen = nextWordDist(soFar).sample();
			if(gen.equals(wstr(STOP))){
				return soFar;
			}else{
				String[] tmp = soFar;
				soFar = new String[soFar.length+1];
				for(int i=0; i<soFar.length-1; i++){
					soFar[i] = tmp[i];
				}
				soFar[soFar.length-1] = gen;
			}
		}
	}
	
	private double getSentenceProb(String[] sentence, boolean stop){
		//(initial tail)
		Tuple tail = null;
		Tuple lastTerm = tail;
		for(int i=0; i<(depth-1); i++){ 
			tail = new Tuple(START, tail); 
			if(lastTerm == null){ lastTerm = tail; } //last non-null term
		}
		//(aggregate probs)
		double rollingProb = 1.0;
		for(String word : sentence){
			//(get prob)
			int w = toLocalWord(word);
			rollingProb *= getCondProb(w, tail);
			//(shift tail over)
			lastTerm.setCdr(new Tuple(w, null));
			tail = tail.cdr();
			lastTerm = lastTerm.cdr();
		}
		//(stop prob)
		if(stop){ rollingProb *= getCondProb(STOP, tail); }
		return rollingProb;
	}
	public double getSentenceProb(String[] sentence){
		return getSentenceProb(sentence, true);
	}
	public double getSentenceFragmentProb(String[] fragment){
		return getSentenceProb(fragment, false);
	}
	
	private double getSentenceLogProb(String[] sentence, boolean stop){
		//(initial tail)
		Tuple tail = null;
		Tuple lastTerm = tail;
		for(int i=0; i<(depth-1); i++){ 
			tail = new Tuple(START, tail); 
			if(lastTerm == null){ lastTerm = tail; } //last non-null term
		}
		//(aggregate probs)
		double rollingProb = 0.0;
		for(String word : sentence){
			//(get prob)
			int w = toLocalWord(word);
			rollingProb += Math.log( getCondProb(w, tail) );
			//(shift tail over)
			lastTerm.setCdr(new Tuple(w, null));
			tail = tail.cdr();
			lastTerm = lastTerm.cdr();
		}
		//(stop prob)
		if(stop){ rollingProb += Math.log( getCondProb(STOP, tail) ); }
		return rollingProb;
	}

	public double getSentenceLogProb(String[] sentence){
		return getSentenceLogProb(sentence, true);
	}
	public double getSentenceFragmentLogProb(String[] fragment){
		return getSentenceLogProb(fragment, false);
	}
	
	
	public String[] infer(){
		String[] soFar = new String[0];
		while(true){
			String gen = nextWordDist(soFar).infer();
			if(gen.equals(wstr(STOP))){
				return soFar;
			}else{
				String[] tmp = soFar;
				soFar = new String[soFar.length+1];
				for(int i=0; i<soFar.length-1; i++){
					soFar[i] = tmp[i];
				}
				soFar[soFar.length-1] = gen;
			}
		}
	}
}
