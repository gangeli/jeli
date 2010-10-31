package org.goobs.exec;

import java.util.LinkedList;
import java.util.Queue;

public class Channel <Type> {
	
	private Queue <Type> queue = new LinkedList <Type> ();
	
	public synchronized void send(Type toSend){
		queue.offer(toSend);
		this.notify();
	}
	
	public synchronized Type recieve(){
		while(queue.isEmpty()){
			try {
				this.wait();
			} catch (InterruptedException e) {}
		}
		Type rtn = queue.poll();
		return rtn;
	}
	
	public synchronized boolean isEmpty(){
		boolean rtn = queue.isEmpty();
		return rtn;
	}
}
