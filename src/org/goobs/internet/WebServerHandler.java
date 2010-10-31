package org.goobs.internet;

import java.util.HashMap;

public interface WebServerHandler {
	public String handle(HashMap<String,String> values, WebServer.HttpInfo info);
}
