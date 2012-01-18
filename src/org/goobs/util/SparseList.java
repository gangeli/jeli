package org.goobs.util;

import java.util.HashMap;

public class SparseList <E> {
	
	private HashMap <Integer, E> map = new HashMap <Integer, E> ();
	
	public SparseList(){
		
	}
	
	public E get(int index){
		return map.get(index);
	}
	
	public E set(int index, E value){
		return map.put(index, value);
	}
	
	public boolean hasIndex(int index){
		return map.containsKey(index);
	}
	
}
