package org.goobs.exec;

public class ConcurrencyException extends RuntimeException{
	/**
	 * 
	 */
	private static final long serialVersionUID = -654220667488413244L;
	public ConcurrencyException(){
		super();
	}
	public ConcurrencyException(String msg){
		super(msg);
	}
}
