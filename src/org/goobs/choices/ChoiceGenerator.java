package org.goobs.choices;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Stack;
import java.io.File;

import org.goobs.functional.Function;
import org.goobs.utils.Pair;
import org.goobs.utils.PrettyPrinter;
import org.goobs.search.*;
import org.goobs.foreign.Counter;

public class ChoiceGenerator <Input, Output>{
		
	public static final int PROHIBIT_NULL_GEN 	= 0x1 << 0;
	public static final int SAMPLE 				= 0x1 << 1;
	public static final int DEBUG				= 0x1 << 2;
	public static final int LOG_CHOICES			= 0x1 << 3;
	public static final int DRY_RUN				= 0x1 << 4;
	
	public static final class ParentInfo <ParentInput, ParentOutput> {
		public ParentInput pIn;
		public ParentOutput pOut;
		private ParentInfo(ParentInput pIn, ParentOutput pOut){
			this.pIn = pIn;
			this.pOut = pOut;
		}
	}
	
	public static final class ForwardInfo<ParentInput,ParentOutput,ChildInput>{
		public ParentInput pIn;
		public ParentOutput pOut;
		public ChildInput hist;
		private ForwardInfo(ParentInput in, ParentOutput out, ChildInput hist){
			this.pIn = in;
			this.pOut = out;
			this.hist = hist;
		}
	}
	public static final class BackInfo<ChildInput,ChildOutput,ParentInput>{
		public ChildInput cIn;
		public ChildOutput cOut;
		public ParentInput pIn;
		private BackInfo(ChildInput cIn, ChildOutput cOut, ParentInput pIn){
			this.cIn = cIn;
			this.cOut = cOut;
			this.pIn = pIn;
		}
	}
	
	public static final class Generation <Output> {
		
		private Object[][] data;
		private double score;

		private Generation(Node[] reverseGenerations, double score){
			this.score = score;
			data = new Object[reverseGenerations.length][reverseGenerations[0].size()];
			for(int level=0; level<reverseGenerations.length; level++){
				Node n = reverseGenerations[level];
				for(int i=data[level].length-1; i>=0; i--){
					if(n == null) throw new IllegalStateException("Node should not be null!");
					data[level][i] = n.content;
					n = n.next;
				}
			}
		}
		
		private Generation(GenerationTree tree, int levels, int length, double score){
			this.score = score;
			//(variables)
			Object[] vertical = new Object[levels];
			int depth = 0;
			int index = 0;
			this.data = new Object[levels][length];
			

			Stack <GenerationTree> genStack = new Stack <GenerationTree> ();
			Stack <Integer> posStack = new Stack <Integer> ();
			
			int child = 0;
			GenerationTree parent = tree;
			
			while(parent != null){
				//(variables)
				GenerationTree node = parent.children[child];
				Object content = node.content;
				//System.out.println("depth=" + genStack.size() + ": " + content);
				//(cases)
				if(content == TREE_STOP){
					//stop term: pop up
					if(genStack.isEmpty()){
						parent = null;
					}else{
						parent = genStack.pop();
						child = posStack.pop();
						//System.out.println("Popping: " + parent.content + " child=" + child);
					}
				}else{
					depth = genStack.size();
					vertical[depth] = content;
					if(depth >= levels-1){
						//leaf node: move right and pop up
						for(int i=0; i<vertical.length; i++){
							data[i][index] = vertical[i];
						}
						index += 1;
						child += 1;
					}else{
						//middle node: push down
						//System.out.println("Pushing: " + parent.content + " child=" + (child+1));
						genStack.push(parent);
						posStack.push(child+1);
						parent = node;
						child = 0;
					}
				}
			}
			
			
		}

		public double getScore(){ return score; }
		
		public int size(){
			if(data.length > 0){
				return data[0].length;
			}else{
				return 0;
			}
		}
		
		public int levels(){
			return data.length;
		}
		
		public final Object get(int level, int index){
			return data[level][index];
		}
		
		@SuppressWarnings("unchecked")
		public Output[] getGenerated(){
			return (Output[]) data[data.length-1];
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object o){
			if(o instanceof Generation<?>){
				Generation <Output> gen = (Generation<Output>) o;
				if(this.levels() != gen.levels()) return false;
				if(this.data[0].length != gen.data[0].length) return false;
				for(int level=0; level<this.levels(); level++){
					for(int i=0; i<this.data[level].length; i++){
						if(!get(level,i).equals(gen.get(level,i))){
							return false;
						}
					}
				}
				return true;	//success condition
			}
			return false;
		}

		@Override
		public String toString(){
			Output[] gen = getGenerated();
			StringBuilder b = new StringBuilder();
			for(Output out : gen){
				b.append(out.toString()).append("  ");
			}
			return b.toString();
		}
	}
	
	private static final class Edge <ParentInput,ParentOutput,ChildInput,ChildOutput>{
		private Function <ParentInfo <ParentInput, ParentOutput>, AChoice <ChildInput, ChildOutput>> child;
		private Function <ForwardInfo<ParentInput,ParentOutput,ChildInput>, ChildInput> forward;
		private Function <BackInfo<ChildInput,ChildOutput,ParentInput>, ParentInput> backward;
		private Edge(
				Function <ParentInfo <ParentInput, ParentOutput>, AChoice <ChildInput, ChildOutput>> child,
				Function <ForwardInfo<ParentInput,ParentOutput,ChildInput>, ChildInput> forward,
				Function <BackInfo<ChildInput,ChildOutput,ParentInput>, ParentInput> backward ) {
			this.child = child;
			this.forward = forward;
			this.backward = backward;
		}
	}
	
	private AChoice <Input,Object> firstChoice;
	
	//note: we kind of just assume type safety here
	private List <Edge<Object,Object,Object,Object>> edges 
		= new ArrayList <Edge<Object,Object,Object,Object>> ();
	
	private int levels;
	
	private int genBuffer = 2048383;	//127^3 = 127 fannout for 3 levels (around 20M total)
	//private int genBuffer = 10000;		//10^4 = 10 fannout for 4 levels (10k total)

	@SuppressWarnings("unchecked")
	public ChoiceGenerator(AChoice <Input,? extends Object> firstLevel){
		this.firstChoice = (AChoice <Input, Object>) firstLevel;
		this.levels = 1;
	}
	
	public <ParentInput,ChildInput,ChildOutput> ChoiceGenerator <Input,ChildOutput>
		append(
			Function <ParentInfo <ParentInput, Output>, AChoice <ChildInput, ChildOutput>> nextChoice,
			Function <ForwardInfo<ParentInput,Output,ChildInput>, ChildInput> forward
		){
		return append(nextChoice, forward,
			new Function <BackInfo<ChildInput,ChildOutput,ParentInput>,ParentInput>(){
				private static final long serialVersionUID = -7699188697853752040L;
				@Override
				public ParentInput eval(BackInfo<ChildInput,ChildOutput,ParentInput> info){
					return info.pIn;
				}
			}
		);
	}


	@SuppressWarnings("unchecked")
	public <ParentInput,ChildInput,ChildOutput> ChoiceGenerator <Input, ChildOutput> 
		append(
			Function <ParentInfo <ParentInput, Output>, AChoice <ChildInput, ChildOutput>> nextChoice,
			Function <ForwardInfo<ParentInput,Output,ChildInput>, ChildInput> forward,
			Function <BackInfo<ChildInput,ChildOutput,ParentInput>, ParentInput> backward
		){
		
		ChoiceGenerator <Input, ChildOutput> rtn = new ChoiceGenerator<Input,ChildOutput>(firstChoice);
		rtn.edges = new ArrayList<Edge<Object,Object,Object,Object>>(this.edges);
		rtn.edges.add(
				(Edge<Object,Object,Object,Object>)
				new Edge<ParentInput,Output,ChildInput,ChildOutput>(nextChoice, forward, backward));
		rtn.levels = this.levels + 1;
		
		return rtn;
	}


	private static final class Node{
		private Object content;
		private Node next;
		private Node(Object content, Node next){
			this.content = content;
			this.next = next;
		}
		private int size(){
			if(next == null){
				return 1;
			} else {
				return 1 + next.size();
			}
		}
	}

	private static final byte FLAG_NONE = 0;
	private static final byte FLAG_PROHIBIT_STOP = 1;
	private static final byte FLAG_NEED_TO_FILL = 2;

	private final class GenerationState implements SearchState{
		private Node[] reverseGenerations;
		@SuppressWarnings("unchecked")
		private ChoiceInstance.Saved[] states;
		private byte level;
		private byte stateFlags = FLAG_NONE;

		private GenerationState(int levels){
			if(levels > Byte.MAX_VALUE) throw new IllegalArgumentException("Too many levels!");
			this.reverseGenerations = new Node[levels];
			this.states = new ChoiceInstance.Saved[levels];
		}

		private void ensure(boolean toCheck, String msg){
			if(!toCheck) throw new IllegalStateException(msg);
		}
		private void ensureValid(){
			ensure(reverseGenerations.length == states.length, "generations and states have different dimensions");
			if(reverseGenerations[0] == null) return;
			int size = reverseGenerations[0].size();
			for(int i=0; i<states.length; i++){
				if(size > 1){
					ensure(reverseGenerations[i].size() == size || reverseGenerations[i].size() == size-1, 
						"Generation grid should be equi-sized; size=" + size + ", calc=" + reverseGenerations[i].size() +
						" bad term=" + this);
				}
			}
		}

		private boolean getFlag(byte flag){ return (stateFlags & flag) != 0; }
		private void setFlag(byte flag){ stateFlags |= flag; }

		private int levels(){ return reverseGenerations.length; }

		@SuppressWarnings("unchecked")
		private GenerationState copy(){
			GenerationState child = new GenerationState(this.levels());
			child.reverseGenerations = new Node[this.reverseGenerations.length];
			System.arraycopy(this.reverseGenerations, 0, child.reverseGenerations, 0, this.reverseGenerations.length);
			child.states = new ChoiceInstance.Saved[this.states.length];
			for(int i=0; i<child.states.length; i++){
				if(this.states[i] != null) child.states[i] = new ChoiceInstance.Saved(this.states[i]);
				else child.states[i] = null;
			}
			child.level = this.level;
			child.stateFlags = FLAG_NONE;
			return child;
		}

		@SuppressWarnings("unchecked")
		private void goUp(
					int childLevel,
					ChoiceInstance parentGenerator,
					ChoiceInstance childGenerator,
					Object childOutput){
				//variables
				this.level = (byte) (childLevel-1);
				Edge<Object,Object,Object,Object> edge = edges.get(this.level); //edge from the "upper" node
				//backwards message pass for history
				backInfo.cIn = childGenerator.history(); 
				backInfo.cOut = childOutput; 
				backInfo.pIn = parentGenerator.history();
				this.states[this.level].setHistory( edge.backward.eval(backInfo) );
				//fill parents
				this.setFlag(FLAG_NEED_TO_FILL);
		}

		@SuppressWarnings("unchecked")
		private boolean goDown(
				int parentLevel, 
				ChoiceInstance parentGenerator, 
				Object parentOutput,
				ChoiceInstance lastChildGenerator){
			//(variables)
			this.level = (byte) (parentLevel+1);
			Edge<Object,Object,Object,Object> edge = null;
			if(parentLevel < edges.size()){
				edge = edges.get(parentLevel); //edge from the "upper" node
			}
			if(edge != null){
				//(set up child generator)
				//child choice
				parentInfo.pIn = parentGenerator.history(); parentInfo.pOut = parentOutput;
				AChoice<Object,Object> childChoice = edge.child.eval(parentInfo); //child function
				//child history
				Object childLastHistory = null;
				if(lastChildGenerator != null)
					childLastHistory = lastChildGenerator.history();
				else
					childLastHistory = childChoice.noHistory(); 
				forwardInfo.pIn = parentGenerator.history(); 
				forwardInfo.pOut = parentOutput; 
				forwardInfo.hist = childLastHistory;
				Object childHistory = edge.forward.eval(forwardInfo); //forward function
				//child generator
				ChoiceInstance<Object,Object> childGenerator = new ChoiceInstance(childChoice, childHistory);
				this.states[this.level] = childGenerator.save();
				this.setFlag(FLAG_PROHIBIT_STOP);
				return true;
			}else{
				this.level = (byte) parentLevel;
				return false;
				//we stay at the same level
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public Counter<SearchState> children(double needToBeat){
			if(isStopState()){ throw new IllegalStateException("Cannot take the children of a stop node"); }
			Counter<SearchState> children = new Counter<SearchState>();

			ChoiceInstance generator = states[level].load();
			Counter <Object> outputs = generator.outputs(getFlag(FLAG_PROHIBIT_STOP));
			for(Object out: outputs.keySet()){
				//--Variables
				double prob = outputs.getProb(out);
				double logProb = Math.log(prob);
				if(logProb < needToBeat){
					continue; //don't bother making a child if it's getting thrown out anyways
				}
				GenerationState child = this.copy();
				children.setCount(child,logProb);
				//--Register Output
				if(out == generator.getChoice().stopTerm()){
					if(getFlag(FLAG_PROHIBIT_STOP)) throw new IllegalStateException("Stop term pulled when prohibited");
					//(case: go up)
					//note: don't register stop state
					if(this.level == 0){
						//can't go up - create stop
						child.level = -1;
					}else{
						//else go up
						child.goUp(
							this.level,
							this.states[level-1].load(),
							generator,
							out
						);
					}
				} else {
					//(case: go down (or stay if bottom level))
					//fill if applicable
					if(getFlag(FLAG_NEED_TO_FILL)){
						for(int i=0; i<this.level-1; i++){
							child.reverseGenerations[i] 
								= new Node(this.reverseGenerations[i].content, this.reverseGenerations[i]);
						}
					}
					//go down
					ChoiceInstance lastChildGen = null;
					if(level+1 < states.length && states[level+1] != null) lastChildGen = states[level+1].load();
					boolean wentDown =
						child.goDown(
							this.level,
							generator,
							out,
							lastChildGen
						);
					//register: this is in preperation for the next generation
					child.states[this.level].register(out);
					child.reverseGenerations[this.level] = new Node(out, this.reverseGenerations[this.level]);
					if((!wentDown && !getFlag(FLAG_PROHIBIT_STOP))){ 
						//fill parents
						if(this.level < this.levels()-1) throw new IllegalStateException("Should not go across yet!");
						for(int i=0; i<this.level; i++){
							child.reverseGenerations[i] 
								= new Node(this.reverseGenerations[i].content, this.reverseGenerations[i]);
						}
					}
				}
				child.ensureValid();
			}
			
			return children;
		}
		
		@Override
		public boolean isStopState(){
			return this.level < 0;
		}
		@Override
		public String toString(){
			StringBuilder b = new StringBuilder();
			b.append("GenState(");
			for(int i=0; i<levels(); i++){
				if(reverseGenerations[i] == null) b.append("0, ");
				else b.append(reverseGenerations[i].size()).append(", ");
			}
			b.append(")");
			return b.toString();
		}
	}
	
	
	private static final class GenerationTree{
		private Object content;
		private ChoiceInstance <Object,Object> generator;
		private GenerationTree[] children;
	}
	private static final Object TREE_STOP = new Pair<String,String>("STOP","TERM");	//some unique object
	private GenerationTree tree;
	private ForwardInfo<Object,Object,Object> forwardInfo = new ForwardInfo<Object,Object,Object>(null,null,null);
	private BackInfo<Object,Object,Object> backInfo = new BackInfo<Object,Object,Object>(null,null,null);
	private ParentInfo<Object,Object> parentInfo = new ParentInfo<Object,Object>(null,null);
	private int terminalsGenerated = 0;
	private double logScore;
	
	private void ensureGenerationTree(){
		//determine fanout
		int fannout = (int) Math.floor( Math.pow((double) genBuffer, 1.0 / ((double) levels)) );
		if(fannout < 1){
			throw new IllegalStateException("Fannout is too small!");
		}
		
		if(tree == null){
			//(root)
			tree = new GenerationTree();
			if(levels <= 0){ return; } 
			tree.children = new GenerationTree[fannout];
			Stack <Pair<GenerationTree,Integer>> enqueue = new Stack<Pair<GenerationTree,Integer>>();
			enqueue.add(new Pair<GenerationTree,Integer>(tree,levels));
			//(recursive)
			while(!enqueue.isEmpty()){
				Pair<GenerationTree,Integer> p = enqueue.pop();
				int level = p.cdr();
				if(level > 0){
					GenerationTree[] toFill = p.car().children;
					for(int i=0; i<toFill.length; i++){
						GenerationTree tmp = new GenerationTree();
						if(level > 1){ tmp.children = new GenerationTree[fannout]; }
						toFill[i] = tmp;
						enqueue.add(new Pair<GenerationTree,Integer>(tmp,level-1));
					}
					toFill[toFill.length-1].content = TREE_STOP;		//last term is always a stop
				}
			}
		}
	}
	
	private static File choiceLogFile;

	@SuppressWarnings("unchecked")
	private Pair<Object,Object> greedyGenerateRec(
			GenerationTree parent,
			int level,
			int flags,
			PrettyPrinter printer
			){
		//--Digest Flags
		boolean prohibitNullGen = ((flags & PROHIBIT_NULL_GEN) != 0);
		boolean sample = ((flags & SAMPLE) != 0);
		boolean logChoices = ((flags & LOG_CHOICES) != 0);
		boolean dryRun = ((flags & DRY_RUN) != 0);
		//--Basic Information
		ChoiceInstance <Object,Object> generator = parent.generator;
		Edge<Object,Object,Object,Object> edge = null;
		if(level < edges.size()){
			edge = edges.get(level);
		}

		//--Recursive Generate
		Object value = null;
		Object gold = null;
		int index = 0;
		while( index < parent.children.length-1 ){	//this is a theoretical upper bound only
			//(infer child value)
			double scoreAdd = 0.0;
			//debug: get true choice
			if(forcedOutputs != null){
				if(forcedOutputs.isEmpty()) throw new IllegalStateException("Generated more terms than should have");
				gold = forcedOutputs.removeFirst();
				if(!dryRun){
					Counter <Object> outs = generator.outputs(prohibitNullGen && (index==0)); //must be before register
					if(outs.keySet().contains(gold)){
						scoreAdd = Math.log(outs.getCount(gold));
					}else{
						scoreAdd = 0.0; //default: no effect on the score
					}
				}
			}
			//infer
			if(dryRun){
				value = null;	//We'll be using the gold value
			}else if(sample){
				value = generator.sample(prohibitNullGen && (index==0));
			}else{
				value = generator.infer(prohibitNullGen && (index==0));
			}
			//increment score
			if(forcedOutputs == null){ scoreAdd = Math.log(generator.lastChoiceProb()); }
			logScore += scoreAdd;
			//debug: log choice
			if(logChoices) generator.log(choiceLogFile, value, gold, prohibitNullGen && (index==0));
			//debug: force true output
			if(forcedOutputs != null && gold != null) value = gold;
			//debug: call callback
			if(callback != null){
				if(generator == null) throw new IllegalStateException("Null choice instance!");
				if(generator.getChoice() == null) throw new IllegalStateException("Generator's choice is null!");
				callback.choiceMade(generator.getChoice().getClass(), generator.history(), value);
			}
			//register the output
			generator.register(value);
			//save value and check if we're done
			if(value == generator.getChoice().stopTerm()){
				if(index == 0 && prohibitNullGen){
					System.err.println("Level=" + level);
					System.err.println("Debug=" + (printer != null));
					throw new IllegalStateException("Returned stop term when shouldn't (prohibitStop set)");
				}
				parent.children[index].content = TREE_STOP; //mark special tree-stop object
				if(printer != null && index == 0) printer.newline();
				break;
			}else{
				parent.children[index].content = value;
			}
			if(printer != null){ printer.logRight(value.toString(), generator.lastChoiceProb()); }
			//recursive step
			if(edge != null){
				//(set up child generator)
				//child choice
				parentInfo.pIn = generator.lastHistory(); parentInfo.pOut = value;
				AChoice<Object,Object> childChoice = edge.child.eval(parentInfo);
				//child history
				Object childLastHistory = null;
				if(index > 0)
					childLastHistory = parent.children[index-1].generator.history();
				else
					childLastHistory = childChoice.noHistory(); 
				forwardInfo.pIn = generator.history(); forwardInfo.pOut = value; forwardInfo.hist = childLastHistory;
				Object childHistory = edge.forward.eval(forwardInfo);
				//child generator
				ChoiceInstance<Object,Object> childGenerator = new ChoiceInstance(childChoice, childHistory);
				parent.children[index].generator = childGenerator;
				//(generate from child)
				Pair<Object,Object> childHistVal
					= greedyGenerateRec(parent.children[index], level+1, flags, printer);
				//backwards message pass for history
				backInfo.cIn = childHistVal.car(); backInfo.cOut = childHistVal.cdr(); backInfo.pIn = generator.history();
				generator.setHistory( edge.backward.eval(backInfo) );
			}else{
				terminalsGenerated += 1;
				if(printer != null){ printer.newline(); printer.backup(); }
			}
			index += 1;
		}
		if(index == parent.children.length - 1) System.err.println("WARNING: filled up the generation tree buffer");
		if(printer != null && level > 0) printer.backup();
		//return final history and output
		return new Pair<Object,Object>(generator.history(),value);
	}
	
	@SuppressWarnings("unchecked")
	public synchronized Generation <Output> generateGreedy(
			Input firstHistory, 
			int flags
			){
		//overhead
//		boolean prohibitNullGen = ((flags & PROHIBIT_NULL_GEN) != 0);
//		boolean sample = ((flags & SAMPLE) != 0);
		boolean debug = ((flags & DEBUG) != 0);
		boolean logChoices = ((flags & LOG_CHOICES) != 0);
		boolean dryRun = ((flags & DRY_RUN) != 0);
		if(dryRun && forcedOutputs == null) throw new IllegalStateException("Dry run must have a forced output");
		if(logChoices && !isFileOk(choiceLogFile)) throw new IllegalStateException("Logging choices to invalid file");
		terminalsGenerated = 0;
		logScore = 0.0;
		ensureGenerationTree();
		//set initial choice
		tree.generator = new ChoiceInstance(firstChoice,firstHistory);
		//generate
		PrettyPrinter printer = null;
		if(debug) printer = new PrettyPrinter(10);
		greedyGenerateRec(tree, 0, flags, printer);
		double score = logScore;
		forcedOutputs = null;
		return new Generation <Output> (tree, levels, terminalsGenerated, score);
	}

	public static interface ChoiceCallback{
		public void choiceMade(Class<? extends Object> choiceType, Object hist, Object output);
	}

	public synchronized void dryRun(
			Input firstHistory, 
			LinkedList<Object> outputs, 
			ChoiceCallback callback,
			int flags){
		forcedOutputs = outputs;
		ChoiceGenerator.callback = callback;
		generateGreedy(firstHistory, flags | DRY_RUN);
		ChoiceGenerator.callback = null;
		forcedOutputs = null;
	}

	private KBestSearch search = null;
	@SuppressWarnings("unchecked")
	public synchronized Generation <Output> generateKBest(
			Input firstHistory,
			int k,
			int flags
			){
		//overhead
//		boolean prohibitNullGen = ((flags & PROHIBIT_NULL_GEN) != 0);
//		boolean sample = ((flags & SAMPLE) != 0); // N/A
//		boolean debug = ((flags & DEBUG) != 0); // Too much output
		boolean logChoices = ((flags & LOG_CHOICES) != 0); // TODO implement me
		if(logChoices && !isFileOk(choiceLogFile)) throw new IllegalStateException("Logging choices to invalid file");
		//setup start node
		GenerationState firstNode = new GenerationState(edges.size() + 1);
		firstNode.setFlag(FLAG_PROHIBIT_STOP);
		firstNode.states[0] = new ChoiceInstance(firstChoice,firstHistory).save();
		//generate
		if(search == null || k != search.getK()){
			search = new KBestSearch(k);
		}
		Counter<SearchState> results = search.search(new SearchState[] {firstNode}, SearchProblem.GREATEST_SCORE);
		if(results.size() == 0) throw new IllegalStateException("Search did not return any states!");
		GenerationState bestState = (GenerationState) results.argMax();
		double score = results.getCount(results.argMax());
		forcedOutputs = null;
		if(!bestState.isStopState()) throw new IllegalStateException("Search returned non-stop state");
		Generation <Output> rtn = new Generation <Output> (bestState.reverseGenerations, score);
		//k=1 greedy check
		/*
		if(k==1){
			Generation<Output> check = generateGreedy(firstHistory, flags);
			if(!check.equals(rtn)){
				System.err.println("Greedy:\n" + check);
				System.err.println("\nK-best:\n" + rtn);
				throw new IllegalStateException("Greedy generator differs from k=1 k-best");
			}
		}
		*/
		//return
		return rtn;
	}

	private boolean isFileOk(File f){
		if(f == null) return false;
		return true;
	}
	
	public synchronized static void setChoiceLogFile(String filename){
		try{
			choiceLogFile = new File(filename);
			if(!choiceLogFile.exists()){
				choiceLogFile.createNewFile();
			}
		}catch(Exception e){
			choiceLogFile = null;
		}
	}
	
	private static LinkedList<Object> forcedOutputs = null;
	private static ChoiceCallback callback = null;
	public synchronized static void forceNextChoiceSequence(LinkedList<Object> outputs){
		forcedOutputs = outputs;
	}
	
	public static void main(String[] args){
		ChoiceGenerator<Integer,Integer> gen = new ChoiceGenerator<Integer, Integer>(null);
		gen = gen.append(null, null, null);
		gen = gen.append(null, null, null);
		gen.ensureGenerationTree();
	}
}
