package org.goobs.io;

public interface ArduinoListener {
	//(handle line and handle chunk is the same information)
	public void handleLine(String str);
	public void handleChunk(byte[] chunk);
	
	public void handleException(Exception e);
}
