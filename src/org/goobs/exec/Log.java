/**
 * 
 */
package org.goobs.exec;

import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import java.io.FileWriter;
import java.io.File;

import org.goobs.utils.Stopwatch;

public final class Log {
	
	static final class LogInfo{
		private long lastTick = 0;
		private Stopwatch timer = new Stopwatch();
		int depth;
		String lastPrinted = null;
		private boolean isFirst = true;
		private int numLinesSkipped = 0;
		private int numSameLinesPrinted = 1;
		
		private boolean isPrinting = true;
		
		private LogInfo(int depth, boolean isPrinting){
			this.depth = depth;
			this.isPrinting = isPrinting;
			timer.start();
		}
		private final String preEndTrack(){
			StringBuilder b = new StringBuilder();
			if(numLinesSkipped > 0){
				b.append(preLog());
				b.append("...").append(numLinesSkipped).append(" similar lines")
					.append("\n");
				numLinesSkipped = 0;
			}
			for(int i=0; i<depth; i++){
				b.append("  ");
			}
			System.out.print(b);
			return b.toString();
		}
		private final String preLog(){
			StringBuilder b = new StringBuilder();
			if(isFirst){
				b.append(" {\n");
				isFirst = false;
			}
			for(int i=0; i<depth+1; i++){
				b.append("  ");
			}
			System.out.print(b);
			return b.toString();
		}
		private final String showTime(){
			StringBuilder b = new StringBuilder();
			long time = timer.getElapsedTime();
			if(isPrinting && time > Log.MIN_TIME_TOPRINT){
				b.append(" [").append(Stopwatch.formatTimeDifference(time))
					.append("]\n");
			}else if(isPrinting){
				b.append("\n");
			}
			System.out.print(b);
			return b.toString();
		}
		private boolean shouldPrint(String toPrint, boolean force){
			if(!isPrinting){
				return false;
			}
			if(force){ return true; }
			long t = timer.getElapsedTime();
			boolean rtn = true;
			boolean isDifferent = false;
			//--Get Should Print
			if(lastPrinted == null){
				//case: first printed
				rtn =  true;
			}else{
				int matchLength = Math.min(Log.MATCH_BOUNDARY, Math.min(toPrint.length(), lastPrinted.length()));
				if((matchLength == Log.MATCH_BOUNDARY || toPrint.length() == lastPrinted.length()) &&
						toPrint.substring(0, matchLength).equals(lastPrinted.substring(0, matchLength))){
					if(t - lastTick > Log.TIME_INTERVAL){
						//case: same term, but enough time
						numSameLinesPrinted += 1;
						rtn =  true;
					}else{
						//case: same term, not enough time
						if(numSameLinesPrinted >= Log.MIN_LOG_COUNT){
							rtn =  false;
						}else{
							numSameLinesPrinted += 1;
							rtn = true;
						}
					}	
				}else{
					//case: not the same term
					numSameLinesPrinted = 1;
					isDifferent = true;
					rtn =  true;
				}
			}
			//--Update State
			if(rtn){
				lastPrinted = toPrint;
				lastTick = t;
				if(numLinesSkipped > 0 && isDifferent){
					String s = preLog();
					if(shouldFileLog){ fileLog(s); }
					String str = "..." + numLinesSkipped + " similar lines";
					System.out.println(str);
					if(shouldFileLog){ fileLog(str + "\n"); }
					numLinesSkipped = 0;
				}
			}else{
				numLinesSkipped += 1;
			}
			//--Return
			return rtn;
		}
	}
	
	private static final long MIN_TIME_TOPRINT = 1000;
	private static final long TIME_INTERVAL = 1000;
	private static final int MATCH_BOUNDARY = 5;
	private static final int MIN_LOG_COUNT = 3;

	
	@Option(gloss="Print debugging log entries")
	private static boolean logDebug = false;
	
	private static final Stack <LogInfo> levels = new Stack<LogInfo>();
	private static LogInfo currentInfo = null;

	private static Lock threadLock;

	private static FileWriter logFile = null;
	private static boolean shouldFileLog = true;
	
	protected static final void signalThreads(){
		if(threadLock != null){ throw fail("Signaling multithreaded environment when already in multithreaded environment"); }
		threadLock = new ReentrantLock();
	}
	
	protected static final void endThreads(){
		threadLock = null;
	}
	
	public static final void beginTrack(String name){
		startTrack(name);
	}
	
	public static final void start_track(String name){ startTrack(name); }
	public static final void end_track(){ endTrack(); }

	public static final void startTrack(String name) {
		if(currentInfo != null){
			levels.push(currentInfo);
			if(currentInfo.shouldPrint(name, false)){
				String s = currentInfo.preLog();
				if(shouldFileLog){ fileLog(s); }
				System.out.print(name);
				if(shouldFileLog){ fileLog(name); }
				currentInfo = new LogInfo(currentInfo.depth+1, currentInfo.isPrinting);
			}else{
				currentInfo = new LogInfo(currentInfo.depth+1, false);
			}
		}else{
			System.out.print(name);
			if(shouldFileLog){ fileLog(name); }
			currentInfo = new LogInfo(0, true);
		}
	}

	public static final void endTrack() {
		if(currentInfo == null){ fail("Ended track that was never begun!"); }
		if(currentInfo.isPrinting){
			String s = currentInfo.preEndTrack();
			if(shouldFileLog){ fileLog(s); }
			if(currentInfo.lastPrinted != null){
				System.out.print("} ");
				if(shouldFileLog){ fileLog("} "); }
			}
			String time = currentInfo.showTime();
			if(shouldFileLog){ fileLog(time); }
		}

		if(levels.isEmpty()) currentInfo = null; 
		else currentInfo = levels.pop();
	}
	
	//TODO multiple types of fileLog
	private static final void fileLog(String str){
		//(ensure file)
		if(shouldFileLog && logFile == null){
			try{
				File f = Execution.touch("log");
				if(f == null){ return; } //probably execDir not set
				logFile = new FileWriter(f);
			} catch(IOException e) {
				shouldFileLog = false;
			}
		}
		//(write to file)
		if(shouldFileLog){ 
			try{ 
				logFile.write(str); 
				logFile.flush();
			} catch(IOException e){
				System.out.println(" <<WARNING: LOGGING FAILED>> ");
				shouldFileLog = false;
			} 
		}
	}

	private static final void commonPrintStd(Object o, boolean force){
		if(threadLock != null){ threadLock.lock(); }
		
		String str = o.toString();
		if(currentInfo != null && (currentInfo.shouldPrint(str, force))){
			//(print object)
			String printed = currentInfo.preLog();
			fileLog(printed);
			System.out.print(str);
			fileLog(str);
			//(print threading info)
			if(threadLock == null){
				System.out.println();
				fileLog("\n");
			}else{
				String toLog = " <Thread " + Thread.currentThread().getId() + ">";
				System.out.println(toLog);
				fileLog(toLog + "\n");
			}
		}else if(currentInfo == null){
			System.out.println(str);
		}

		if(threadLock != null){ threadLock.unlock(); }
	}
	
	public static void debug(Object o){
		debug(null, o, false);
	}
	public static void debug(String tag, Object o){
		debug(tag, o, false);
	}
	public static void debug(String tag, Object o, boolean force) {
		if(logDebug) commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void debugG(Object o) {
		if(logDebug) commonPrintStd(o,true);
	}

	public static void log(Object o){
		log(null, o, false);
	}
	public static void log(String tag, Object o, boolean force) {
		commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void logG(Object o) {
		commonPrintStd(o,true);
	}
	public static void warn(Object o){
		warn(null, o, false);
	}
	public static void warn(String tag,Object o){
		warn(tag, o, false);
	}
	public static void warn(String tag, Object o, boolean force) {
		commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void warnG(Object o) {
		commonPrintStd(o,true);
	}
	public static void err(Object o){
		err(null, o, false);
	}
	public static void err(String tag, Object o){
		err(tag, o, false);
	}
	public static void err(String tag, Object o, boolean force) {
		commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void errG(Object o) {
		commonPrintStd(o,true);
	}
	
	public static void todo(Object o){
		commonPrintStd("TODO: " + o, true);
	}

	public static RuntimeException internal(Object msg) {
		return new RuntimeException("INTERNAL: " + msg.toString());
	}
	
	public static RuntimeException impossible() {
		return new RuntimeException("Impossible event happened");
	}

	public static RuntimeException fail(Object msg) {
		return new RuntimeException(msg.toString());
	}

	public static RuntimeException fail(Throwable cause) {
		return new RuntimeException(cause);
	}
	
	public static final void fatal(Throwable cause){
		err("FATAL EXCEPTION CAUGHT:");
		cause.printStackTrace();
		exit(ExitCode.FATAL_EXCEPTION);
	}
	
	public static void exit(){ exit(ExitCode.UNKNOWN, true); }
	public static void exit(ExitCode code){ exit(code, true); }
	public static void exit(ExitCode code, boolean hardExit){
		//(close everything up)
		while(!levels.isEmpty()){
			endTrack();
		}
		if(currentInfo != null) endTrack();
		try{
			if(logFile != null){ logFile.close(); }
		} catch(IOException e){}
		//(exit)
		System.exit(code.code);
	}
}
