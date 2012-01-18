package org.goobs.util;

public class Marker {

	private boolean isSet = false;
	
	public void set(){
		isSet = true;
	}
	
	public void unset(){
		isSet = false;
	}
	
	public boolean isSet(){
		return isSet;
	}
}
