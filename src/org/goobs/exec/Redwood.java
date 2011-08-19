package org.goobs.exec;

import java.util.*;

public class Redwood {

	private static final String CLASSNAME_PREFIX = Redwood.class.getCanonicalName();

	private static enum Flag {
		ERROR,
		WARNING,
		DEBUG,
		FORCE
	}
	public static final Flag ERR   = Flag.ERROR;
	public static final Flag WARN  = Flag.WARNING;
	public static final Flag DBG   = Flag.DEBUG;
	public static final Flag FORCE = Flag.FORCE;

	/**
	 *
	 */
	public static class Record {
		//(filled in at construction)
		public final Object content;
		private final Object[] tags;
		public final int depth;
		public final String callingClass;
		public final String callingMethod;
		//(known at creation)
		public final long timesstamp = System.currentTimeMillis();
		public final long thread = Thread.currentThread().getId();
		//(state)
		private boolean tagsSorted = false;

		private Record(Object content, Object[] tags, int depth) {
			//(set fields)
			this.content = content;
			this.tags = tags;
			this.depth = depth;
			//(get calling class)
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			int i = 0;
			while(i < stack.length-1 && stack[i].getClassName().startsWith(CLASSNAME_PREFIX)){
				i += 1;
			}
			this.callingClass = stack[i].getClassName();
			this.callingMethod = stack[i].getMethodName();
		}

		private void sort(){
			//(sort flags)
			if(!tagsSorted && tags.length > 1){
				Arrays.sort(tags, new Comparator<Object>(){
					@Override
			 		public int compare(Object a, Object b){
						if(a == FORCE){ return -1; }
						else if(b == FORCE){ return  1; }
						else if(a instanceof Flag && !(b instanceof Flag)){ return -1; }
						else if(b instanceof Flag && !(a instanceof Flag)){ return  1; }
						else{ return a.toString().compareTo(b.toString()); }
					}
				});
			}
		}

		public boolean force(){ sort(); return this.tags.length > 0 && this.tags[0] == FORCE; }
		public Object[] tags(){ sort(); return this.tags; }
	}

	/**
	 *
	 */
	public static interface Handler {
		public boolean handle(Record record);
		public void signalStartTrack(Record signal);
		public void signalEndTrack(int newDepth);
	}

	/**
	 *
	 */
	public static abstract class OutputHandler implements Handler{
		protected LinkedList<Record> queuedTracks = new LinkedList<Record>();
		protected Stack<Long> timeStack = new Stack<Long>();
		protected String tab = "  ";
		protected int leftMargin = 20;

		private boolean missingOpenBracket = false;

		public abstract void print(String line);

		private void writeContent(int depth, Object content, StringBuilder b){
			if(leftMargin > 2){ b.append(tab); }
			//(write tabs)
			for(int i=0; i<depth; i++){
				b.append(tab);
			}
			//(write content)
			 b.append(content.toString());
		}

		private void updateTracks(){
			while(!queuedTracks.isEmpty()){
				Record signal = queuedTracks.removeFirst();
				StringBuilder b = new StringBuilder();
				if(missingOpenBracket){ b.append("{\n"); }
				//(write margin)
				for(int i=0; i<leftMargin; i++){
					b.append(" ");
				}
				//(write name)
				writeContent(signal.depth,signal.content,b);
				if(signal.content.toString().length() > 0){ b.append(" "); }
				//(print)
				print(b.toString());
				this.missingOpenBracket = true;  //only set to false if actually updated track state
			}
		}

		@Override
		public boolean handle(Record record) {
			StringBuilder b = new StringBuilder();
			//--Handle Tracks
			updateTracks();
			if(this.missingOpenBracket){
				b.append("{\n");
				this.missingOpenBracket = false;
			}
			//--Write Tags
			//(variables)
			int cursorPos = 0;
			boolean stillNeedToPrintContent = true;
			//(loop)
			if(leftMargin > 2){	//don't print if not enough space
				if(record.tags().length > 0){ b.append("["); cursorPos =+ 1; }
				for(int i=0; i<record.tags().length; i++) {
					Object tag = record.tags()[i];
					if(tag == FORCE){ continue; }
					//(get tag)
					String toPrint = tag.toString();
					if(toPrint.length() > leftMargin-1){ toPrint = toPrint.substring(0,leftMargin-2); }
					if(cursorPos+toPrint.length() >= leftMargin){
						//(case: doesn't fit)
						while(cursorPos < leftMargin){ b.append(" "); cursorPos += 1; }
						if(stillNeedToPrintContent){
							writeContent(record.depth,record.content,b);
							stillNeedToPrintContent = false;
						}
						b.append("\n ");
						cursorPos = 1;
					}
					//(print flag)
					b.append(toPrint);
					if(i < record.tags().length-1){ b.append(","); cursorPos += 1; }
					cursorPos += toPrint.length();
				}
				if(record.tags().length > 0){ b.append("]"); cursorPos += 1; }
			}
			//--Content
			//(write content)
			if(stillNeedToPrintContent){
				while(cursorPos < leftMargin){ b.append(" "); cursorPos += 1; }
				writeContent(record.depth, record.content, b);
			}
			//(print)
			b.append("\n");
			print(b.toString());
			//--Continue
			return true;
		}

		@Override
		public void signalStartTrack(Record signal) {
			//(queue track)
			this.queuedTracks.addLast(signal);
			this.timeStack.push(signal.timesstamp);
			//(force print)
			if(signal.force()){
				updateTracks();
			}
		}

		@Override
		public void signalEndTrack(int newDepth) {
			long beginTime = this.timeStack.pop();
			if(this.queuedTracks.isEmpty()){
				StringBuilder b = new StringBuilder();
				if(!this.missingOpenBracket){
					//(write margin)
					for(int i=0; i<this.leftMargin; i++){
						b.append(" ");
					}
					//(null content)
					writeContent(newDepth, "", b);
					//(write bracket)
					b.append("} ");
				}
				this.missingOpenBracket = false;
				//(write time)
				long time = System.currentTimeMillis();
				if(time-beginTime > 100){
					b.append("[").append(formatTimeDifference(time-beginTime)).append("]");
				}
				//(print)
				b.append("\n");
				print(b.toString());
			} else {
				this.queuedTracks.removeLast();
			}
		}
	}

	/**
	 *
	 */
	public static class ConsoleHandler extends OutputHandler {
		@Override
		public void print(String line) { System.out.print(line); }
	}

	/**
	 *
	 */
	public static class VisibilityHandler implements Handler {
		private static enum State { SHOW_ALL, HIDE_ALL }

		private State defaultState = State.SHOW_ALL;
		private final HashSet<Object> deltaPool = new HashSet<Object>();

		public void showAll() {
			this.defaultState = State.SHOW_ALL;
			this.deltaPool.clear();
		}

		public void hideAll() {
			this.defaultState = State.HIDE_ALL;
			this.deltaPool.clear();
		}

		public boolean alsoShow(Object filter){
			switch(this.defaultState){
				case HIDE_ALL:
					return this.deltaPool.add(filter);
				case SHOW_ALL:
					return this.deltaPool.remove(filter);
				default:
					throw new IllegalStateException("Unknown default state setting: " + this.defaultState);
			}
		}

		public boolean alsoHide(Object filter){
			switch(this.defaultState){
				case HIDE_ALL:
					return this.deltaPool.remove(filter);
				case SHOW_ALL:
					return this.deltaPool.add(filter);
				default:
					throw new IllegalStateException("Unknown default state setting: " + this.defaultState);
			}
		}

		@Override
		public boolean handle(Record record) {
			switch (this.defaultState){
				case HIDE_ALL:
					for(Object tag : record.tags){ //note: unsorted tags
						if(this.deltaPool.contains(tag)){ return true; }
					}
					return false;
				case SHOW_ALL:
					for(Object tag : record.tags){ //note: unsorted tags
						if(this.deltaPool.contains(tag)){ return false; }
					}
					return true;
				default:
					throw new IllegalStateException("Unknown default state setting: " + this.defaultState);
			}
		}

		@Override
		public void signalStartTrack(Record signal) {	}
		@Override
		public void signalEndTrack(int newDepth) { }
	}


	private static LinkedList<Handler> handlers = new LinkedList<Handler>();
	private static int depth = 0;
	private static Stack<String> titleStack = new Stack<String>();

	public static boolean removeHandler(Handler toRemove) {
		boolean rtn = false;
		Iterator<Handler> iter = handlers.iterator();
		while(iter.hasNext()){
			if(iter.next().getClass().equals(toRemove.getClass())){
				rtn = true;
				iter.remove();
			}
		}
		return rtn;
	}

	public synchronized static void appendHandler(Handler toAdd){
		handlers.addLast(toAdd);
	}

	public synchronized static void prependHandler(Handler toAdd){
		handlers.addFirst(toAdd);
	}

	@SuppressWarnings("unchecked")
	public static <E extends Handler> E getHandler(Class<E> clazz){
		for(Handler cand : handlers){
			if(clazz == cand.getClass()){
				return (E) cand;
			}
		}
		return null;
	}


	public static void log(Object... args) {
		//(argument checks)
		if(args.length == 0){ return; }
		//(create record)
		Object content = args[args.length-1];
		Object[] tags = new Object[args.length-1];
		System.arraycopy(args,0,tags,0,args.length-1);
		Record toPass = new Record(content,tags,depth);
		//(send record to handlers)
		Iterator<Handler> iter = handlers.iterator();
		while( iter.hasNext() && iter.next().handle(toPass) ){}
	}

	public synchronized static void startTrack(Object... args){
		//(increment depth)
		depth += 1;
		int len = args.length == 0 ? 0 : args.length-1;
		titleStack.push(args.length == 0 ? "" : args[len].toString());
		//(create record)
		Object content = args.length == 0 ? "" : args[len];
		Object[] tags = new Object[len];
		System.arraycopy(args,0,tags,0,len);
		Record toPass = new Record(content,tags,depth-1);
		//(send signal to handlers)
		for (Handler handler : handlers) {
			handler.signalStartTrack(toPass);
		}
	}

	public synchronized static void endTrack(String title){
		//(decrement depth)
		depth -= 1;
		String expected = titleStack.pop();
		if(!expected.equalsIgnoreCase(title)){
			throw new IllegalArgumentException("Track names do not match: expected: " + expected + " found: " + title);
		}
		//(send signal to handlers)
		for (Handler handler : handlers) {
			handler.signalEndTrack(depth);
		}
	}

	public synchronized static void endTrack(){ endTrack(""); }


	public static boolean showOnlyChannel(Object channel){
		VisibilityHandler handler = getHandler(VisibilityHandler.class);
		if(handler == null){ throw new IllegalStateException("No visibility handler found"); }
		handler.hideAll();
		return handler.alsoShow(channel);
	}
	public static boolean hideChannel(Object channel){
		VisibilityHandler handler = getHandler(VisibilityHandler.class);
		if(handler == null){ throw new IllegalStateException("No visibility handler found"); }
		return handler.alsoHide(channel);
	}
	public static void printChannels(int width){
		ConsoleHandler handler = getHandler(ConsoleHandler.class);
		if(handler == null){ throw new IllegalStateException("No console handler found"); }
		handler.leftMargin = width;
	}
	public static void printChannels(){ printChannels(20); }
	public static void dontPrintChannels(){ printChannels(0); }






	private static String formatTimeDifference(long diff){
		//--Get Values
		int mili = (int) diff % 1000;
		long rest = diff / 1000;
		int sec = (int) rest % 60;
		rest = rest / 60;
		int min = (int) rest % 60;
		rest = rest / 60;
		int hr = (int) rest % 24;
		rest = rest / 24;
		int day = (int) rest;
		//--Make String
		StringBuilder b = new StringBuilder();
		if(day > 0) b.append(day).append(day > 1 ? " days, " : " day, ");
		if(hr > 0) b.append(hr).append(hr > 1 ? " hours, " : " hour, ");
		if(min > 0) {
			if(min < 10){ b.append("0"); }
			b.append(min).append(":");
		}
		if(min > 0 && sec < 10){ b.append("0"); }
		b.append(sec).append(".").append(mili);
		if(min > 0) b.append(" minutes");
		else b.append(" seconds");
		return b.toString();
	}



	static {
		appendHandler(new VisibilityHandler());
		appendHandler(new ConsoleHandler());
	}

	public static void main(String[] args){
		Redwood.dontPrintChannels();


		startTrack("Track 1");
			log("tag",ERR, "hello world");
			startTrack("Hidden");
				startTrack("Subhidden");
				endTrack("Subhidden");
			endTrack("Hidden");
			startTrack(FORCE,"Shown");
				startTrack(FORCE,"Subshown");
				endTrack("Subshown");
			endTrack("Shown");
			log("^shown should have appeared above");
			startTrack("Track 1.1");
				log(WARN,"some","something in 1.1");
				log("some",ERR,"something in 1.1");
				log(FORCE,"some",WARN,"something in 1.1");
				log(WARN,FORCE,"some","something in 1.1");
			endTrack("Track 1.1");
			startTrack();
				log("In an anonymous track");
			endTrack();
		endTrack("Track 1");
		log("outside of a track");
		log("these","tags","should","be","in",DBG,"alphabetical","order", "a log item with lots of tags");
		log(DBG,"a last log item");
	}

}
