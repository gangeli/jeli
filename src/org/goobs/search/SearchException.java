package org.goobs.search;

public class SearchException extends RuntimeException{
	private static final long serialVersionUID = 7216758539801682384L;
	public SearchException(){
		super();
	}
	public SearchException(String msg){
		super(msg);
	}
	public SearchException(Throwable e){
		super(e);
	}
}
