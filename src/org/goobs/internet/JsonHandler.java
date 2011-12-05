package org.goobs.internet;

import com.sun.net.httpserver.Headers;

import java.util.HashMap;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public abstract class JsonHandler implements WebServerHandler {
  public abstract String handleJSON(HashMap<String,String> values, WebServer.HttpInfo info);
  @Override
  public final String handle(HashMap<String, String> values, WebServer.HttpInfo info) {
    String callback = null;
    if(values.containsKey("callback")){
      callback = values.get("callback");
      values.remove("callback");
    }else if(values.containsKey("jsoncallback")){
      callback = values.get("jsoncallback");
      values.remove("jsoncallback");
    } else {
      throw new IllegalArgumentException("No callback specified");
    }
    return ""+callback+"(\n"+handleJSON(values, info)+"\n)";
  }

  @Override
  public void setHeaders(Headers responseHeaders) {
    responseHeaders.set("Content-Type", "application/json");
    responseHeaders.set("Cache-Control", "no-cache");
  }
}
