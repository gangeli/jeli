package org.goobs.qry;

import edu.stanford.nlp.util.MetaClass;
import org.goobs.database.Database;
import org.goobs.net.WebServer;
import org.goobs.net.WebServerHandler;

import java.io.File;

public class Qry {
	
	public static final int PORT = 4242;
	public static final Class[] HANDLERS = new Class[]{
		ViewHandler.class,
		DomainHandler.class,
		DataHandler.class,
	};
	
	public static void main(String[] args){
		//--Error Check
		System.out.println("Parsing Arguments");
		if(args.length != 1){
			System.err.println("USAGE: Qry [database]");
			System.exit(1);
		}
		//--Create Database
		System.out.println("Connecting To Database");
		Database results = new Database(args[0]);
		results.connect();
		//--Create Webserver
		System.out.println("Creating Webserver");
		WebServer server = new WebServer(PORT);
		server.start();
		//--Adding Handlers
		System.out.println("Adding Handlers {");
		//(index)
		System.out.println("  mounting /");
		server.mount("/", new File("src/aux/qry/"));
		for(Class handlerClass : HANDLERS) {
			System.out.println("  adding /" + handlerClass.getSimpleName());
			//(create handler)
			WebServerHandler handler = (WebServerHandler) MetaClass.create(handlerClass).createInstance(results);
			//(register handler)
			server.register("/"+handlerClass.getSimpleName(), handler);
		}
		System.out.println("}");



//		Stopwatch timer = new Stopwatch();
//		timer.start();
//		for(int q=0; q<20; q++){
//		for(int i=0; i<1175; i++){
//			Iterator<DBResultLogger.Option> x = results.getObjectsByKey(DBResultLogger.Option.class, "rid", 1175);
//			while(x.hasNext()){
//				x.next();
//			}
//		}
//			System.out.println("Sweep " + q + " in: " + Stopwatch.formatTimeDifference(timer.lap()));
//		}
//		System.out.println(Stopwatch.formatTimeDifference(timer.getElapsedTime()));
//		System.exit(0);
	}
}
