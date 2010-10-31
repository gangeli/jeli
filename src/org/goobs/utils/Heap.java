package org.goobs.utils;

import java.util.NoSuchElementException;

import org.goobs.exec.Log;

public abstract class Heap <E>{

	private E[] objects;
	private double[] scores;
	private Object[] extras = null;
	private int size = 0;
	private long maxCapacity = Long.MAX_VALUE;
	
	public Heap(){
		this(16);
	}
	public Heap(int initialCapacity){
		this(initialCapacity, Integer.MAX_VALUE);
	}
	public Heap(int initialCapacity, long maxCapacity){
		this.maxCapacity = maxCapacity;
		if(maxCapacity > Integer.MAX_VALUE) Log.warn("Maximum capacity overflows Integer.MAX_VALUE");
		setCapacity(initialCapacity);
		this.size = 0;
	}
	
	protected abstract void heapifyUp(int pos);
	protected abstract void heapifyDown(int pos);
	
	public final double peekScore(){
		return scores[0];
	}
	public final Object peekExtra(){
		if(extras == null) return null;
		else return extras[0];
	}
	public final E peek(){
		return objects[0];
	}
	
	public final E pop(){
		if(size == 0) throw new NoSuchElementException();
		//(remove element)
		E rtn = objects[0];
		objects[0] = objects[size-1];
		scores[0] = scores[size-1];
		if(extras != null){ extras[0] = extras[size-1]; }
		size -= 1;
		//(heapify)
		heapifyDown(0);
		return rtn;
	}
	
	public final void push(E term, double score){
		push(term, score, null);
	}
	
	public final void push(E term, double score, Object extra){
		//(grow if needed)
		if(size >= objects.length){
			if(objects.length == 0){ setCapacity(16); }
			setCapacity(objects.length * 2);
		}
		//(add element)
		objects[size] = term;
		scores[size] = score;
		if(extra != null){
			if(extras == null){
				extras = new Object[objects.length];
			}
			extras[size] = extra;
		}
		size += 1;
		//(heapify)
		heapifyUp(size-1);
	}
	
	public final boolean isEmpty(){
		return size == 0;
	}
	
	public final int size(){
		return size;
	}
	
	@SuppressWarnings("unchecked")
	private final void keepHalf(Object[] objects, double[] scores, Object[] extras){
		if(objects == null || objects.length == 0){
			this.objects = (E[]) new Object[1];
			this.scores = new double[1];
			this.extras = new Object[1];
		}else{
			this.objects = (E[]) objects;
			this.scores = scores;
			this.extras = extras;
			//--Get Top Objects
			for(int i=0; i<objects.length/2; i++){
				//(get the highest object)
				int pos = objects.length-i-1;
				double score = peekScore();
				Object extra = peekExtra();
				E obj = pop();
				//(save the object)
				this.objects[pos] = obj;
				this.scores[pos] = score;
				if(extras != null){
					this.extras[pos] = extra;
				}
			}
			//--Clear Heap
			this.size = 0;
			//--Add Elements
			//add one less to allow for at least one element more
			for(int pos=this.objects.length-this.objects.length/2; pos<this.objects.length; pos++){
				push(this.objects[pos], scores[pos], extras == null ? null : extras[pos]);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private final void setCapacity(int capacity){
		if(this.size >= maxCapacity){
			//(case: at capacity)
			Log.err("Heap cannot grow (max capacity=" + maxCapacity + "); Dropping low priority items...");
			keepHalf(this.objects, this.scores, this.extras);
			return;
		}
		//--Try to Grow
		E[] saveObj = this.objects;
		double[] saveScore = this.scores;
		Object[] saveExtras = this.extras;
		try {
			//(grow heap)
			this.objects = (E[]) new Object[(int) Math.min((long) capacity,maxCapacity)];
			this.scores = new double[(int) Math.min((long) capacity,maxCapacity)];
			this.extras = new Object[(int) Math.min((long) capacity,maxCapacity)];
		} catch (OutOfMemoryError e) {
			//(case: out of memory)
			Log.err("Heap cannot grow (insufficient memory); Dropping low priority items...");
			keepHalf(saveObj, saveScore, saveExtras);
			return;
		}
		//(copy if applicable)
		if(saveObj != null && saveScore != null){
			System.arraycopy(saveObj, 0, this.objects, 0, saveObj.length);
			System.arraycopy(saveScore, 0, this.scores, 0, saveScore.length);
			if(extras != null){
				System.arraycopy(saveExtras, 0, this.extras, 0, saveExtras.length);
			}
		}
	}
	
	protected final int parent(int index){
		return (index-1)/2;
	}
	
	protected final int leftChild(int parent){
		return 2*parent+1;
	}
	protected final int rightChild(int parent){
		return 2*parent+2;
	}
	protected final void swap(int posA, int posB){
		E tmpObj = objects[posA];
		double tmpScore = scores[posA];
		Object tmpExtra = extras == null ? null : extras[posA];
		objects[posA] = objects[posB];
		scores[posA] = scores[posB];
		objects[posB] = tmpObj;
		scores[posB] = tmpScore;
		if(extras != null){
			extras[posA] = extras[posB];
			extras[posB] = tmpExtra;
		}
	}
	protected final double score(int pos){
		return scores[pos];
	}
	
}
