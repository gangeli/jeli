package org.goobs.scheme;

public class SchemeException extends RuntimeException {

	private static final long serialVersionUID = -6333457838090964364L;
	
	public SchemeException(){
		super();
	}
	
	public SchemeException(String s){
		super(s);
	}
	
	public void printError(){
		System.err.println("*** Error:");
		System.err.println("    " + getMessage());
	}
	
	public Throwable fillInStackTrace() {
		  return this;
	}

	
}
