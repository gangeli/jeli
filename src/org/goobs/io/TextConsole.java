package org.goobs.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class TextConsole implements Console{

	private boolean isActive = false;
	private BufferedReader reader;
	
	@Override
	public Console show() {
		reader = new BufferedReader(new InputStreamReader(System.in));
		isActive = true;
		return this;
	}
	
	@Override
	public boolean isShowing() {
		return isActive;
	}

	@Override
	public void print(Object o) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(o);
	}
	@Override
	public void print(int x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(x);
	}

	@Override
	public void print(double x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(x);
	}

	@Override
	public void print(boolean x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(x);
	}

	@Override
	public void print(long x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(x);
	}

	@Override
	public void println(Object o) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.println(o);
	}

	@Override
	public void println(int x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.println(x);
	}

	@Override
	public void println(double x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.println(x);
	}

	@Override
	public void println(boolean x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.println(x);
	}

	@Override
	public void println(long x) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.println(x);
	}
	
	private String generalRead(String prompt){
		if(!isActive){
			throw new ConsoleDiedException();
		}
		System.out.print(prompt);
		try {
			return reader.readLine();
		} catch (IOException e) {
			isActive = false;
			throw new ConsoleDiedException();
		}
	}

	@Override
	public Boolean readBoolean(String prompt) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		while(true){
			String input = generalRead(prompt).toLowerCase();
			try {
				if(input.equals("true") || input.equals("yes") || input.equals("y") || input.equals("ok")){
					return true;
				}else{
					return false;
				}
			} catch (NumberFormatException e) {
				println("Invalid boolean: " + input);
			}
		}
	}

	@Override
	public Boolean readBoolean() {
		return readBoolean("T/F?");
	}

	@Override
	public Double readDouble(String prompt) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		while(true){
			String input = generalRead(prompt);
			try {
				return Double.parseDouble(input);
			} catch (NumberFormatException e) {
				println("Invalid number: " + input);
			}
		}
	}

	@Override
	public Double readDouble() {
		return readDouble("num?");
	}

	@Override
	public Integer readInteger(String prompt) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		while(true){
			String input = generalRead(prompt);
			try {
				return Integer.parseInt(input);
			} catch (NumberFormatException e) {
				println("Invalid integer: " + input);
			}
		}
	}

	@Override
	public Integer readInteger() {
		return readInteger("int?");
	}

	@Override
	public String readLine(String prompt) {
		return generalRead(prompt);
	}

	@Override
	public String readLine() {
		return readLine("input?");
	}

	@Override
	public Long readLong(String prompt) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		while(true){
			String input = generalRead(prompt);
			try {
				return Long.parseLong(input);
			} catch (NumberFormatException e) {
				println("Invalid integer: " + input);
			}
		}
	}

	@Override
	public Long readLong() {
		return readLong("int?");
	}

	@Override
	public String readUntil(String prompt, int length) {
		return readUntil(prompt, (char) 0, length);
	}

	@Override
	public String readUntil(String prompt, char end) {
		return readUntil(prompt, end, Integer.MAX_VALUE);
	}

	@Override
	public String readUntil(String prompt, char end, int maxLength) {
		if(!isActive){
			throw new ConsoleDiedException();
		}
		StringBuilder b = new StringBuilder();
		for(int i=0; i<maxLength; i++){
			char term;
			try {
				term = (char) reader.read();
			} catch (IOException e) {
				throw new ConsoleDiedException();
			}
			if(term == end){
				return b.toString();
			}else{
				b.append(term);
			}
		}
		return b.toString();
	}

}
