package org.goobs.utils;

import java.io.*;
import java.util.*;

/*
 * Original Author: Dan Klein
 */
public class Indexer<E> extends AbstractList<E> implements Serializable {
	private static final long serialVersionUID = -8769544079136550516L;
	private Map<E, Integer> obj2index;
	private List<E> index2obj;

	public Indexer() {
		obj2index = new HashMap<E, Integer>();
		index2obj = new ArrayList<E>();
	}
	
	public Indexer(Collection<? extends E> c) {
		this();
		addAll(c);
	}
	
	public Iterator<E> getObjects() {
		return index2obj.iterator();
	}

	/**
	 * Return the object with the given index
	 * 
	 * @param index
	 */
	public E get(int index) {
		return index2obj.get(index);
	}

	/**
	 * Returns the number of objects indexed.
	 */
	public int size() {
		return index2obj.size();
	}

	/**
	 * Returns the index of the given object, or -1 if the object is not present
	 * in the indexer.
	 * 
	 * @param o
	 * @return
	 */
	public int indexOf(Object o) {
		Integer index = obj2index.get(o);
		if (index == null)
			return -1;
		return index;
	}

	/**
	 * Add an element to the indexer if not already present. In either case,
	 * returns the index of the given object.
	 * 
	 * @param e
	 * @return
	 */
	public int addAndGetIndex(E e) {
		Integer index = obj2index.get(e);
		if (index != null) {
			return index;
		}
		int newIndex = size();
		obj2index.put(e, newIndex);
		index2obj.add(e);
		return newIndex;
	}

	/**
	 * Constant time override for contains.
	 */
	public boolean contains(Object o) {
		return obj2index.keySet().contains(o);
	}

	/**
	 * Add an element to the indexer. If the element is already in the indexer,
	 * the indexer is unchanged (and false is returned).
	 * 
	 * @param e
	 * @return
	 */
	public boolean add(E e) {
		if (contains(e))
			return false;
		obj2index.put(e, size());
		index2obj.add(e);
		return true;
	}
}
