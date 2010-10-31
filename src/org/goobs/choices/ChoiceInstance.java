package org.goobs.choices;

import java.io.File;
import java.io.FileWriter;

import org.goobs.utils.Pair;
import org.goobs.classify.OutputInfo;
import org.goobs.foreign.Counter;

public class ChoiceInstance <Input, Output> {
	
	public static final class Saved<Input,Output>{
		private AChoice<Input,Output> choice;
		private Input in;
		public Saved(AChoice<Input,Output> choice, Input in){
			this.choice = choice;
			this.in = in;
		}
		public Saved(Saved<Input,Output> saved){
			this.choice = saved.choice;
			this.in = saved.in;
		}
		public ChoiceInstance<Input,Output> load(){
			return new ChoiceInstance<Input,Output>(choice,in);
		}
		public void register(Output out){
			if(out != choice.stopTerm()){
				//only feedback if not the stop term
				Input inSave = in;
				in = choice.feedback(in, out);
				if(in == inSave){ throw new IllegalStateException("call to feedback() cannot modify previous history"); }
			}
		}
		public void setHistory(Input in){
			this.in = in;
		}
	}
	public Saved <Input,Output> save(){
		return new Saved<Input,Output>(choice,in);
	}

	private AChoice<Input,Output> choice;
	private Input in;
	private Input lastIn;
	private double lastProb;
//	private Pair <Output,Double> toFill = new Pair<Output,Double>(null,null);
	
	public ChoiceInstance(AChoice<Input,Output> choice){
		this.choice = choice;
		this.in = choice.noHistory();
		this.lastIn = this.in;
	}
	
	public ChoiceInstance(AChoice<Input,Output> choice, Input input){
		this.choice = choice;
		this.in = input;
		this.lastIn = this.in;
	}
	
	public Input history(){
		return in;
	}
	public Input lastHistory(){
		return lastIn;
	}

	public void setHistory(Input in){
		this.in = in;
		this.lastIn = in;
	}

	@SuppressWarnings("unchecked")
	public <Encode> void log(File file, Object guess, Object gold, boolean prohibitStop){
		FileWriter writer = null;
		String start = "";
		if(guess != gold) start = ">";
		try{
			//--Setup
			if(!(choice instanceof ClassifierChoice)){
				return;	//don't log if not a classifier choice
			}
			writer = new FileWriter(file, true);
			boolean logGold = true;
			if(gold == null){
				gold = guess;	//assume we got it right, if no supervision 
				logGold = false;
			}
			//--Not Classifier
			if( ! (choice instanceof ClassifierChoice) ){
				writer.append(start+"NO CLASSIFIER:: guess=" + guess + " gold=" + gold);
				writer.append(start+"\n-----------------------------\n");
				writer.close();
				return;
			}
			//--Get Info
			Pair <OutputInfo<Encode>,OutputInfo<Encode>> pair = null;
			try{
				pair = ((ClassifierChoice) choice).getInfo(lastIn, guess, gold);
			} catch(Exception e){
				//(catch random exceptions)
				writer.append(start+"EXCEPTION IN CHOICE: " + e.getMessage());
				writer.append(start+"\n-----------------------------\n");
				writer.close();
				return;
			}
			OutputInfo<Encode> guessInfo = pair.car();
			OutputInfo<Encode> goldInfo = pair.cdr();
			//--Dump
			if(guess == choice.stopTerm()){
				writer.append(start+"GUESS=[[STOP]]").append("\n");
			}else{
				writer.append(start+"GUESS= ").append(guess.toString()).append("\n");
			}
			writer.append(start+guessInfo.dump(10).replaceAll("\n", "\n"+start));
			if(logGold){
				if(gold == choice.stopTerm()){
					writer.append(start+"GOLD=[[STOP]]").append("\n");
				}else{
					writer.append(start+"GOLD= ").append(gold.toString()).append("\n");
				}
				writer.append(start+goldInfo.dump(10).replaceAll("\n","\n"+start));
			}
			writer.append("\n-----------------------------\n");
			writer.flush();
			writer.close();
		} catch(Exception e){
			if(writer != null) {
				try{ writer.close(); } catch (Exception ex){ System.err.println("Could not close writer"); }
			}
			e.printStackTrace();
			System.err.println("Exception: " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	public Counter <Output> outputs(boolean prohibitStop){
		if(choice instanceof ClassifierChoice){
			Counter<Output> counts = ((ClassifierChoice<Input,Object,Output>) choice).getProbabilities(in, prohibitStop);
			return counts;
		} else{
			Counter <Output> counts = new Counter<Output>();
			counts.incrementCount(choice.output(in), 1.0);
			return counts;
		}
	}

	public Counter<Output> outputs(){
		return outputs(false);
	}
	
	public void register(Output out){
		if(out != choice.stopTerm()){
			//only feedback if not the stop term
			Input inSave = in;
			lastIn = in;
			in = choice.feedback(in, out);
			if(in == inSave){ throw new IllegalStateException("call to feedback() cannot modify previous history"); }
		}
	}
	
	public Output infer(boolean prohibitStop){
		Output out = null;
		Counter <Output> counts = outputs(prohibitStop);
		out = counts.argMax();
		lastProb = counts.getProb(out);
		return out;
	}

	@SuppressWarnings("unchecked")
	public Output sample(boolean prohibitStop){
		if(choice instanceof ClassifierChoice){
			Counter <Output> counts = outputs(prohibitStop);
			Output out = counts.sample();
			lastProb = counts.getProb(out);
			register(out);
			return out;
		}else{
			return infer(prohibitStop);
		}
		
	}

	public Output inferAndRegister(boolean prohibitStop){
		Output out = infer(prohibitStop);
		register(out);
		return out;
	}
	public Output inferAndRegister(){
		return inferAndRegister(false);
	}
	
	public Output sampleAndRegister(boolean prohibitStop){
		Output out = sample(prohibitStop);
		register(out);
		return out;
	}
	public Output sampleAndRegister(){
		return sampleAndRegister(false);
	}

	public double lastChoiceProb(){
		return lastProb;
	}
	
	public final AChoice<Input,Output> getChoice(){
		return choice;
	}
	
	@Override
	public int hashCode(){
		return in.hashCode();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean equals(Object o){
		if(o instanceof ChoiceInstance){
			if(in.equals(((ChoiceInstance) o).in)){
				return true;
			}
		}
		return false;
	}
	
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("[" + choice.toString() + "]: history=");
		b.append(in.toString());
		return b.toString();
	}
}
