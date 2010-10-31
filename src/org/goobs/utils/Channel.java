package org.goobs.utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Channel <E> {

	private Lock theLock = new ReentrantLock();
	private Condition theCondition = theLock.newCondition();
	private Queue<E> theVariable = new LinkedList<E>();
	
	public void push(E theObject){
		theLock.lock();
		if(!this.theVariable.offer(theObject)){
			throw new IllegalArgumentException("Too many elements in the channel");
		}
		theCondition.signalAll();
		theLock.unlock();
	}
	
	public E pull(){
		theLock.lock();
		while(this.theVariable.isEmpty()){
			theCondition.awaitUninterruptibly();
		}
		E rtn = theVariable.poll();
		theLock.unlock();
		return rtn;
	}
	
	public int sizeLowerBound(){
		theLock.lock();
		int size = this.theVariable.size();
		theLock.unlock();
		return size;
	}
}
