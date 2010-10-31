package org.goobs.internet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.net.httpserver.*;

public class WebServer {
	
	private static final String ICON_REGEX = "/favicon\\.ico";
	
	private HttpServer server;
	private int port;
	private byte[] icon;

	public static final class HttpInfo {
		public HashMap <String,List<String>> headers = new HashMap<String,List<String>>();
	}


	public WebServer(int port){
		this.port = port;
	}

	public void start(){
		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.start();
		} catch (IOException e) {
			throw new RuntimeException("Could not start web server: " + e.getMessage());
		}
	}

	public void register(final String uri, final WebServerHandler handler){
		server.createContext(uri, new HttpHandler(){
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				if(exchange.getRequestMethod().equalsIgnoreCase("GET")){
					OutputStream responseBody = exchange.getResponseBody();
					//--Custom Icon
					if(exchange.getRequestURI().getPath().matches(ICON_REGEX)){
						responseBody.write(getIcon());
						responseBody.close();
						return;
					}
					String query = exchange.getRequestURI().getQuery();
					HashMap <String,String> values = parseQuery(query);
					//--Create Values Map
					//--Create Info
					HttpInfo info = new HttpInfo();
					Headers responseHeaders = exchange.getResponseHeaders();
					responseHeaders.set("Content-Type", "text/html");
					exchange.sendResponseHeaders(200, 0);
					Headers requestHeaders = exchange.getRequestHeaders();
					Set<String> keySet = requestHeaders.keySet();
					Iterator<String> iter = keySet.iterator();
					HashMap <String,List<String>> headers = new HashMap<String,List<String>>();
					while (iter.hasNext()) {
						String key = iter.next();
						List <String> vals = requestHeaders.get(key);
						headers.put(key, vals);
					}
					info.headers = headers;


					responseBody.write( handler.handle(values, info).getBytes() );
					responseBody.close();
				} else {
					System.err.println("[WebServer]: Warning: unhandled request of type: " + exchange.getRequestMethod());
				}
			}
		});
	}
	
	private byte[] getIcon(){
		if(icon == null){
			return "".getBytes();
		}else{
			return icon;
		}
	}
	
	public void setIcon(byte[] icon){
		this.icon = icon;
	}
	
	private static HashMap<String, String> parseQuery(String query) {
		HashMap <String,String> rtn = new HashMap <String,String>();
		if(query == null) return rtn;
		String[] vals = query.split("&");
		for(String val : vals){ 
			String[] v = val.split("=");
			if(v.length != 2){ throw new IllegalArgumentException("Invalid query part: " + val); }
			rtn.put(v[0], v[1]);
		}
		return rtn;
	}

	public static void main(String[] args) throws IOException {
		WebServer server = new WebServer(4242);
		server.start();
		server.register("/", new WebServerHandler(){
			@Override
			public String handle(HashMap<String, String> values, HttpInfo info) {
				return "Done";
			}
		});
	}
}
