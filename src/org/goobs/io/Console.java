package org.goobs.io;

public interface Console {
	
	public static class ConsoleDiedException extends RuntimeException{
		/**
		 * 
		 */
		private static final long serialVersionUID = -1811797909500176333L;
	}

	public void println(Object o);
	public void println(int x);
	public void println(double x);
	public void println(boolean x);
	public void println(long x);
	public void print(Object o);
	public void print(int x);
	public void print(double x);
	public void print(boolean x);
	public void print(long x);
	
	
	public String readUntil(String prompt, int length);

	public String readUntil(String prompt, char end);
	public String readUntil(String prompt, char end, int maxLength);
	public Integer readInteger(String prompt);
	public Double readDouble(String prompt);
	public Long readLong(String prompt);
	public Boolean readBoolean(String prompt);
	public String readLine(String prompt);
	public String readLine();
	public Integer readInteger();
	public Double readDouble();
	public Long readLong();
	public Boolean readBoolean();
	
	
	public Console show(); 
	public boolean isShowing();
}
