package org.goobs.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SingletonIterator <E> implements Iterator <E>, Iterable<E>{

	private E term;
	
	public SingletonIterator(E term){
		this.term = term;
	}
	
	@Override
	public boolean hasNext() {
		return term != null;
	}

	@Override
	public E next() {
		if(term == null){ throw new NoSuchElementException("No more elements in iterator"); }
		E rtn = term;
		this.term = null;
		return rtn;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("remove() not implemented for singleton iterator");
	}

	@Override
	public Iterator<E> iterator() {
		return this;
	}

	public static final <E> SingletonIterator<E> make(E term){
		return new SingletonIterator <E> (term); 
	}
}
