package org.goobs.exec;

public interface ProcessListener {

	public void handleOutputLine(String msg);
	public void handleErrorLine(String msg);
	public void onException(Exception e);
	public void onFinish(int returnVal);
	public void onTimeout();
}
