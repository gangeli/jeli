package org.goobs.distributions;

import java.util.Random;

import org.goobs.foreign.Counter;
import org.goobs.functional.Function;
import org.goobs.utils.Pair;

public class ParticleFilterDistribution <Input,Encode,Type> 
			implements TrainedDistribution<Input,Encode,Type>, 
			DiscreteDistribution<Type>, 
			GradualTrainedDistribution<Input,Encode,Type> {

	//for retraining
	private Particle<Encode,Type> templateStart = null;
	private int templateSize = -1;
	private Particle<Encode,Type>[] templateStarts = null;
	private double[] templateWeights = null;
	
	private Particle<Encode,Type>[] particles;
	private double[] weights;

	private boolean debug = false;
	
	private Counter<Encode> particleFeatureWeights = new Counter<Encode>();
	
	private DiscreteDistribution <Type> inferredDist = null;
	
	@SuppressWarnings("unchecked")
	public ParticleFilterDistribution(Particle<Encode,Type> initParticle, int size){
		if(initParticle == null) throw new IllegalArgumentException("Initial particle is null");
		templateStart = initParticle;
		templateSize = size;
		//(build particles)
		particles = new Particle[size];
		for(int i=0; i<particles.length; i++){
			particles[i] = initParticle.copy();
		}
		//(build weights)
		weights = new double[size];
		double weight = 1.0 / ((double) particles.length);
		for(int i=0; i<weights.length; i++){
			weights[i] = weight;
		}
	}
	
	public ParticleFilterDistribution(Particle<Encode,Type>[] initParticles){
		this(initParticles, new double[initParticles.length]);
		double weight = 1.0 / ((double) initParticles.length);
		for(int i=0; i<weights.length; i++){
			weights[i] = weight;
		}
		this.templateWeights = null;
	}
	
	public ParticleFilterDistribution(Particle<Encode,Type>[] initParticles, double[] initWeights){
		if(initParticles.length != initWeights.length){
			throw new IllegalArgumentException("Particle and weight lengths do not match");
		}
		this.templateStarts = initParticles;
		this.templateWeights = initWeights;
		this.particles = initParticles;
		this.weights = initWeights;
	}
	
	@Override
	public double getProb(Type object) {
		if(inferredDist == null){
			growParticles(Particle.INFINITE_DEPTH);
		}
		return inferredDist.getProb(object);
	}

	@Override
	public Type infer() {
		if(inferredDist == null){
			growParticles(Particle.INFINITE_DEPTH);
		}
		return inferredDist.infer();
	}

	@Override
	public Type sample() {
		if(inferredDist == null){
			growParticles(Particle.INFINITE_DEPTH);
		}
		return inferredDist.sample();
	}
	
	@Override
	public double expectation(Function<Type, Double> func) {
		if(inferredDist == null){
			growParticles(Particle.INFINITE_DEPTH);
		}
		return inferredDist.expectation(func);
	}

	@Override
	public void foreach(Function<Pair<Type, Double>, Object> func) {
		if(inferredDist == null){
			growParticles(Particle.INFINITE_DEPTH);
		}
		inferredDist.foreach(func);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void retrain(Counter<Encode> newWeights) {
		//--Clear Last Distribution
		inferredDist = null;
		//--Set Feature Weights
		this.particleFeatureWeights = newWeights;
		//--Initialize Particles
		if(templateStart != null){
			//(build particles)
			particles = new Particle[templateSize];
			for(int i=0; i<particles.length; i++){
				particles[i] = templateStart.copy();
			}
			//(build weights)
			double weight = 1.0 / ((double) particles.length);
			weights = new double[templateSize];
			for(int i=0; i<weights.length; i++){
				weights[i] = weight;
			}
		}else if(templateWeights != null){
			particles = templateStarts;
			weights = templateWeights;
		}else{
			if(templateStarts == null) throw new IllegalStateException("Nothing to retrain from!");
			particles = templateStarts;
			//(build weights)
			double weight = 1.0 / ((double) particles.length);
			for(int i=0; i<weights.length; i++){
				weights[i] = weight;
			}
		}
	}
	
	@Override
	public Counter<Encode> gradualTrain(Counter<Encode> weightPointer, int depth) {
		//(retrain)
		retrain(weightPointer);
		growParticles(depth);
		
		//--Create the expected counter
		Counter <Encode> rtn = new Counter<Encode>();
		for(int i=0; i<particles.length; i++){
			Particle <Encode,Type> p = particles[i];
			double weight = weights[i];
			Counter <Encode> particleCounts = p.features();
			for(Encode e : particleCounts.keySet()){
				rtn.incrementCount(e, weight * particleCounts.getCount(e));
			}
		}
		//(return)
		return rtn;
	}
	
	
	private void fillSurvivors(int[] survivors){
		//TODO make me efficient
		Random rand = new Random();
		//(chose other particles to survive)
		for(int i=0; i<survivors.length; i++){
			double target = rand.nextDouble();
			double rolling = 0.0;
			int index = -1;
			while(rolling < target){
				rolling += weights[index+1];
				index += 1;
			}
			if(index == -1) throw new java.lang.IllegalStateException("Rand() returned exactly 0.0");
			survivors[i] = index;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void growParticles(int depth){
		//(overhead)
		int[] survivors = new int[particles.length];
		Particle<Encode,Type>[] newParticles = new Particle[particles.length];
		double[] newWeights = new double[particles.length];
		int doneParticles = 0;
		
		while(doneParticles < particles.length){
			double normalizeWeights = 0.0;
			doneParticles = 0;
			//--For Each Iteration
			//(determine survivors)
			fillSurvivors(survivors);
			//(survivor loop)
			for(int i=doneParticles; i<survivors.length; i++){
				//--For Each Survivor
				//(get survivor)
				Particle<Encode,Type> cand = particles[survivors[i]];
				if(cand.isTerminal(depth)){
					newParticles[i] = cand;
					newWeights[i] = weights[survivors[i]];
				}else{
					//(genrate a child)
					Pair< Particle<Encode,Type>, Double > child = cand.makeChild(particleFeatureWeights);
					Particle<Encode,Type> newParticle = child.car();
					double newWeight = child.cdr();
					if(newWeight < 0.0) throw new IllegalStateException("Nothing should have < 0 weight: child weight:" + newWeight);
					newParticles[i] = newParticle;
					newWeights[i] = weights[survivors[i]] * newWeight;
				}
				//(increment normalization)
				normalizeWeights += newWeights[i];
			}
			//(error check)
			if(doneParticles < particles.length && normalizeWeights == 0.0){
				throw new IllegalStateException("Every particle has zero weight");
			}
			//(normalize and copy)
			for(int i=0; i<particles.length; i++){
				particles[i] = newParticles[i];
				weights[i] = newWeights[i] / normalizeWeights;
				if(particles[i].isTerminal(depth)){
					doneParticles += 1;
				}
			}
			//(done loop)
//			for(int i=doneParticles; i<newParticles.length; i++){
//				//(if terminal, keep it the same)
//				Particle part = particles[i];
//				if(part.isTerminal(depth)){
//					double weight = weights[i];
//					//(swap it to the front of the pool)
//					Particle tmpPart = particles[doneParticles];
//					double tmpWeight = weights[doneParticles];
//					particles[doneParticles] = part;
//					weights[doneParticles] = weight;
//					particles[i] = tmpPart;
//					weights[i] = tmpWeight;
//					//(update statistics)
//					doneParticles += 1;
//					activeWeight -= weight;
//				}
//			}
			//(debug)
			if(debug){
				System.out.println("(debug): Active particles: " 
						+ (particles.length - doneParticles) );
				System.out.println("\tlast 5 [active] particles:");
				System.out.println("\t"+ weights[particles.length-1] + ": " + particles[particles.length-1]);
				System.out.println("\t"+ weights[particles.length-2] + ": " + particles[particles.length-2]);
				System.out.println("\t"+ weights[particles.length-3] + ": " + particles[particles.length-3]);
				System.out.println("\t"+ weights[particles.length-4] + ": " + particles[particles.length-4]);
				System.out.println("\t"+ weights[particles.length-5] + ": " + particles[particles.length-5]);
			}
			//(consistency check)
			if(debug){
				double sum = 0.0;
				for(int i=0; i<particles.length; i++){
					sum += weights[i];
				}
				if(Math.abs(sum-1.0) > 0.001) throw new IllegalStateException("Normalized particles don't sum to 1.0");
			}
		}
		
		//--Fill a standard distribution
		//(fill the distribution)
		ProbHash <Type> rtn = new ProbHash <Type> ();
		for(int i=0; i<particles.length; i++){
			rtn.addCount((Type) particles[i].getContent(), weights[i]);
		}
		//(set global variables)
		this.inferredDist = rtn;
	}
	
	public void setDebug(boolean dbg){
		this.debug = dbg;
	}
	
	public void printParticles(){
		for(int i=0; i<particles.length; i++){
			System.out.println(weights[i] + ": " + particles[i]);
		}
	}
	
	public double getCount(Type term){
		int sum = 0;
		for(int i=0; i<particles.length; i++){
			if(particles[i].getContent().equals(term)){
				sum += 1;
			}
		}
		return sum;
	}
	
	
	public static void main(String[] args){
		class Part implements Particle<String,String>{
			private String content;
			private boolean stop;
			public Part(String content, boolean stop){
				this.content = content.trim();
				this.stop = stop;
			}
			@Override
			public Particle<String,String> copy() {
				return new Part(content, stop);
			}
			@Override
			public String getContent() {
				return content;
			}
			@Override
			public boolean isTerminal(int depth) {
				return stop;
			}
			@Override
			public Pair<Particle<String,String>,Double> makeChild(Counter<String> weights) {
				double rand = Math.random();
				if(rand < 0.25){ 
					return new Pair<Particle<String,String>,Double>(new Part(content, true), 1.0); }
				else if(rand >= .25 && rand < .5) { 
					return new Pair<Particle<String,String>,Double>(new Part(content + " A", false), 1.0); }
				else if(rand >= .5 && rand < .75) { 
					return new Pair<Particle<String,String>,Double>(new Part(content + " B", false), 1.0); }
				else if(rand >= .75 && rand <= 1.0) { 
					return new Pair<Particle<String,String>,Double>(new Part(content + " C", false), 1.0); }
				else { throw new IllegalStateException("Random returned >1"); }	
			}
			@Override
			public String toString(){
				if(isTerminal(Particle.INFINITE_DEPTH)){
					return "(done) " + content;
				}else{
					return content;
				}
			}
			@Override
			public Counter<String> features() {
				return null;
			}
		}
		
		ParticleFilterDistribution <Object,String,String> dist 
			= new ParticleFilterDistribution<Object,String,String>(new Part("",false),1000);
		dist.setDebug(true);
		
		
		
		String str = " A";
		dist.getProb(str);
		System.out.println("\n\n------------\n\n");
		
		System.out.println( "Count of '' = " + dist.getCount(""));
		
		//dist.printParticles();
		str = "";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		str = "A";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		str = "B";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		str = "C";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		str = "A B";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		str = "A B C";
		System.out.println( "Probability of " + str + " = " + dist.getProb(str) );
		
	}

	@Override
	public void addCount(Type object, double count) {
		throw new NoSuchMethodError("Cannot add count to a particle filter distribution");
	}

	@Override
	public double totalCount() {
		throw new NoSuchMethodError("Cannot get totalCount a particle filter distribution");
	}

}
