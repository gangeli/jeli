package org.goobs.utils;

import java.io.Serializable;
import java.lang.reflect.Type;

public class Pair <E, F> implements Serializable, Decodable{
	private static final long serialVersionUID = -4684117860320286880L;
	private E e;
	private F f;
	
	public Pair(E e, F f){
		this.e = e;
		this.f = f;
	}
	
	protected Pair(){
		/* for Decodable interface */
		
	}
	
	public E car(){
		return e;
	}
	
	public F cdr(){
		return f;
	}
	
	public void setCar(E car){
		this.e = car;
	}
	
	public void setCdr(F cdr){
		this.f = cdr;
	}
	
	public Object[] toArray(){
		Object[] rtn = new Object[2];
		rtn[0] = car();
		rtn[1] = cdr();
		return rtn;
	}
	
	@SuppressWarnings("unchecked")
	public boolean equals(Object o){
		if(o instanceof Pair){
			return (e == null ? ((Pair) o).e == null : e.equals(((Pair) o).e))
					&& (f == null ? ((Pair) o).f == null : f.equals(((Pair) o).f));
		}
		return false;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Pair<E,F> decode(String encoded, Type[] params) {
		//--Get Parameters
		if(params == null || params.length != 2){
			throw new IllegalArgumentException("Pair must be decoded with exactly 2 type parameters");
		}
		Class carClass = Utils.type2class(params[0]);
		Class cdrClass = Utils.type2class(params[1]);
		//--Get Strings
		String[] terms = Utils.decodeArray(encoded);
		if(terms.length != 2) 
			throw new IllegalArgumentException("Encoded pair must have exactly 2 terms");
		//--Set Objects
		//(cast)
		E objA = (E) Utils.cast(terms[0], carClass);
		if(objA == null) throw new IllegalArgumentException("Cannot assign value: " + terms[0] + " to type: " + e.getClass());
		F objB = (F) Utils.cast(terms[1], cdrClass);
		if(objB == null) throw new IllegalArgumentException("Cannot assign value: " + terms[1] + " to type: " + f.getClass());
		//(set)
		e = objA;
		f = objB;
		return this;
	}
	
	@Override
	public String encode(){
		return Utils.encodeArray(e,f);
	}
	
	
	public int hashCode(){
		return (e == null ? 0 : e.hashCode()) ^ (f == null ? 0 : f.hashCode());
	}
	
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("(").append(e).append(" , ").append(f).append(")");
		return b.toString();
	}
	
	
	public static final <E,F> Pair <E,F> make(E car, F cdr){
		return new Pair<E,F>(car, cdr);
	}
}
