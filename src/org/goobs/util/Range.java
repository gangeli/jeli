package org.goobs.util;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Range implements Decodable, Iterable<Integer> {
	
	/*
		Vars
	*/
	private int startInclusive;
	private int stopExclusive;
	
	/*
		Constructors
	*/
	@SuppressWarnings("unused")
	private Range(){ }
	public Range(int startInclusive, int stopExclusive){
		this.startInclusive = startInclusive;
		this.stopExclusive = stopExclusive;
	}

	/*
		Boolean Checks
	*/
	public boolean inRange(int num){
		return num >= startInclusive && num < stopExclusive;
	}
	
	/*
		Range Getters
	*/
	public int min(){
		return startInclusive;
	}
	public int max(){
		return stopExclusive;
	}
	public int minInclusive(){
		return startInclusive;
	}
	public int minExclusive(){
		return startInclusive-1;
	}
	public int maxInclusive(){
		return stopExclusive-1;
	}
	public int maxExclusive(){
		return stopExclusive;
	}

	private static final Pattern p = Pattern.compile("\\s*([\\[\\(]?)([0-9\\-]+)\\s*[-,]\\s*([0-9\\-]+)([\\]\\)]?)\\s*");
	@Override
	public Decodable decode(String encoded, Type[] typeParams){
		//--Regex
		//(match)
		Matcher m = p.matcher(encoded);
		if(!m.matches()){
			throw new IllegalArgumentException("Not a Range: " + encoded);
		}
		//(get groups)
		String startParen = m.group(1);
		String startStr = m.group(2);
		String stopStr = m.group(3);
		String stopParen = m.group(4);
		//--Parse
		//(error checks)
		if((startParen.equals("") && !stopParen.equals("")) ||
				(!startParen.equals("") && stopParen.equals(""))){
			throw new IllegalArgumentException("Bad range syntax: " + encoded);
		}
		//(vars)
		int start = -1;
		int stop = -1;
		try {
			start = Integer.parseInt(startStr);
			stop = Integer.parseInt(stopStr);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Bad range syntax: " + encoded);
		}
		boolean startInclusive = (startParen.equals("[") ? true : false);
		boolean stopInclusive = (stopParen.equals("]") ? true : false);
		if(startParen.equals("") && stopParen.equals("")){
			startInclusive = true;
			stopInclusive = false;
		}
		//(parse)
		this.startInclusive = startInclusive ? start : start+1;
		this.stopExclusive = stopInclusive ? stop+1 : stop;
		
		return this;
	}
	
	public int size(){
		return this.stopExclusive-this.startInclusive;
	}
	public int length(){
		return this.stopExclusive-this.startInclusive;
	}
	public int norm(){
		return this.stopExclusive-this.startInclusive;
	}
	
	/*
	 * UTILITY METHODS
	 */
	public int toCacheIndex(int index){
		return index-this.startInclusive;
	}

	/*
	 * STANDARD METHODS
	 */
	
	@Override
	public String encode(){
		return "[" + startInclusive + "-" + stopExclusive + ")";
	}
	
	@Override
	public boolean equals(Object o){
		if(o instanceof Range){
			Range other = ((Range) o);
			return other.startInclusive == this.startInclusive && 
				other.stopExclusive == stopExclusive;
		}else{
			return false;
		}
	}
	
	@Override
	public int hashCode(){
		return (startInclusive << 8) ^ stopExclusive;
	}
	
	@Override
	public String toString(){
		return encode();
	}

	@Override
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>(){
			private int value = startInclusive;
			@Override
			public boolean hasNext() {
				return value < stopExclusive;
			}
			@Override
			public Integer next() {
				if(!hasNext()){ throw new NoSuchElementException(); }
				value += 1;
				return value-1;
			}
			@Override
			public void remove() {
				throw new RuntimeException("You're trying to do nonsense. Stop it.");
			}
		};
	}
}
