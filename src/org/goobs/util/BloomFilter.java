package org.goobs.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class BloomFilter <E> implements Set<E>{

	private int numElements = 0;
	private int numBits;
	private byte[] data;
	private Function<E,Integer>[] hashFunctions;
	
	@SuppressWarnings("unchecked")
	public BloomFilter(int bits){
		this(bits, new Function[0]);
	}
	
	public BloomFilter(int bits, Function<E,Integer>[] functions){
		int numBytes = bits / 8;
		if(bits % 8 != 0) numBytes += 1;
		data = new byte[numBytes];
		this.numBits = bits;
		this.hashFunctions = functions;
	}
	
	
	private boolean isSet(int pos){
		pos = pos % numBits;
		if(pos < 0) pos = numBits + pos;
		int index = pos / 8;
		byte bitmask = (byte) (0x1 << (pos % 8));
		return (data[index] & bitmask) != 0;
	}
	
	private void setBit(int pos){
		pos = pos % numBits;
		if(pos < 0) pos = numBits + pos;
		int index = pos / 8;
		byte bitmask = (byte) (0x1 << (pos % 8));
		data[index] = (byte) (data[index] | bitmask);
	}
	
	
	@SuppressWarnings("unchecked")
	public void addHashFunction(Function<E,Integer> hashFn){
		Function<E,Integer>[] save = this.hashFunctions;
		this.hashFunctions = new Function[hashFunctions.length + 1];
		for(int i=0; i<save.length; i++){
			this.hashFunctions[i] = save[i];
		}
		this.hashFunctions[this.hashFunctions.length-1] = hashFn;
	}
	
	public double falsePositiveProbability(){
		double numFxns = (double) this.hashFunctions.length;
		double numBits = (double) this.numBits;
		double numElems = (double) this.numElements;
		return Math.pow(1.0 - Math.exp(-(numFxns*numElems)/numBits), numFxns); 
	}
	
	@Override
	public boolean add(E term) {
		for(Function<E,Integer> hash : hashFunctions){
			setBit(hash.eval(term));
		}
		numElements += 1;
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> arg0) {
		for(E term : arg0){
			add(term);
		}
		return true;
	}

	@Override
	public void clear() {
		data = new byte[data.length];
		numElements = 0;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean contains(Object obj) {
		//(cast)
		E term;
		try {
			term = (E) obj;
		} catch (ClassCastException e) {
			return false;
		}
		//(check)
		for(Function<E,Integer> hash : hashFunctions){
			if(!isSet(hash.eval(term))){
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean containsAll(Collection<?> arg0) {
		for(Object o : arg0){
			if(!contains(o)) return false;
		}
		return true;
	}

	@Override
	public boolean isEmpty() {
		return numElements == 0;
	}
	
	@Override
	public int size() {
		return numElements;
	}

	@Override
	public Iterator<E> iterator() {
		throw new NoSuchMethodError("Cannot iterate over a bloom filter");
	}
	@Override
	public boolean remove(Object arg0) {
		throw new NoSuchMethodError("Cannot remove from a bloom filter");
	}
	@Override
	public boolean removeAll(Collection<?> arg0) {
		throw new NoSuchMethodError("Cannot remove from a bloom filter");
	}
	@Override
	public boolean retainAll(Collection<?> arg0) {
		throw new NoSuchMethodError("Cannot remove (i.e. retainAll) from a bloom filter");
	}
	@Override
	public Object[] toArray() {
		throw new NoSuchMethodError("Cannot enumerate elements of a bloom filter (to convert to an array)");
	}
	@Override
	public <T> T[] toArray(T[] arg0) {
		throw new NoSuchMethodError("Cannot enumerate elements of a bloom filter (to convert to an array)");
	}
	
	
	public static final double optimalHashFunctionCount(double numBits, double numElements){
		return Math.log(2) * numBits / numElements;
	}

}
