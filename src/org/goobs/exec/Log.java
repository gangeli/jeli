/**
 * 
 */
package org.goobs.exec;

import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
		
		boolean isPrinting = true;
		
		private LogInfo(int depth, boolean isPrinting){
			this.depth = depth;
			this.isPrinting = isPrinting;
			timer.start();
		}
		final void preEndTrack(){
			if(numLinesSkipped > 0){
				preLog();
				System.out.println("..." + numLinesSkipped + " similar lines");
				numLinesSkipped = 0;
			}
			for(int i=0; i<depth; i++){
				System.out.print("  ");
			}
		}
		final void preLog(){
			if(isFirst){
				System.out.println(" {");
				isFirst = false;
			}
			for(int i=0; i<depth+1; i++){
				System.out.print("  ");
			}
		}
		void showTime(){
			long time = timer.getElapsedTime();
			if(isPrinting && time > Log.MIN_TIME_TOPRINT){
				System.out.println(" [" + Stopwatch.formatTimeDifference(time) + "]");
			}else if(isPrinting){
				System.out.println();
			}
		}
		boolean shouldPrint(String toPrint, boolean force){
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
					preLog();
					System.out.println("..." + numLinesSkipped + " similar lines");
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
	
	public static final void startTrack(String name) {
		if(currentInfo != null){
			levels.push(currentInfo);
			if(currentInfo.shouldPrint(name, false)){
				currentInfo.preLog();
				System.out.print(name);
				currentInfo = new LogInfo(currentInfo.depth+1, currentInfo.isPrinting);
			}else{
				currentInfo = new LogInfo(currentInfo.depth+1, false);
			}
		}else{
			System.out.print(name);
			currentInfo = new LogInfo(0, true);
		}
	}

	public static final void endTrack() {
		if(currentInfo == null){ fail("Ended track that was never begun!"); }
		if(currentInfo.isPrinting){
			currentInfo.preEndTrack();
			if(currentInfo.lastPrinted != null){
				System.out.print("} ");
			}
			currentInfo.showTime();
		}

		if(levels.isEmpty()) currentInfo = null; 
		else currentInfo = levels.pop();
	}
	
	private static final void commonPrintStd(Object o, boolean force){
		if(threadLock != null){ threadLock.lock(); }
		
		String str = o.toString();
		if(currentInfo != null && (currentInfo.shouldPrint(str, force))){
			//(print object)
			currentInfo.preLog();
			System.out.print(str);
			//(print threading info)
			if(threadLock == null){
				System.out.println();
			}else{
				System.out.println(" <Thread " + Thread.currentThread().getId() + ">");
			}
		}else if(currentInfo == null){
			System.out.println(str);
		}
		
		
		if(threadLock != null){ threadLock.unlock(); }
	}
	
	public static void debug(Object o){
		debug(null, o, false);
	}
	public static void debug(String tag, Object o, boolean force) {
		if(logDebug) commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void debugG(Object o) {
		if(logDebug) System.out.println(o);
	}

	public static void log(Object o){
		log(null, o, false);
	}
	public static void log(String tag, Object o, boolean force) {
		commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void logG(Object o) {
		System.out.println(o);
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
		System.out.println(o);
	}
	public static void err(Object o){
		err(null, o, false);
	}
	public static void err(String tag, Object o, boolean force) {
		commonPrintStd((tag == null ? "" : "[" + tag + "]: ") + o, force);
	}

	public static void errG(Object o) {
		System.out.println(o);
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
	
	public static void exit(ExitCode code){
		//(close everything up)
		while(!levels.isEmpty()){
			endTrack();
		}
		if(currentInfo != null) endTrack();
		//(exit)
		System.exit(code.code);
	}
}