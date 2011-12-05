package org.goobs.utils;

import java.util.Date;

public class Stopwatch {

	private long start = 0;
	private long lastLap = -1;
	private long stop = -1;
	
	public Stopwatch(){}

  public static Stopwatch time(){
    Stopwatch watch = new Stopwatch();
    watch.start();
    return watch;
  }
	
	public long start(){
		this.start = System.currentTimeMillis();
		this.stop = -1;
		this.lastLap = -1;
		return this.start;
	}
	
	public long lap(){
		long rtn = getLap();
		this.lastLap = System.currentTimeMillis();
		return rtn;
	}
	
	public long stop(){
		this.stop = System.currentTimeMillis();
		return stop;
	}
	
	public long getElapsedTime(){
		if(stop >= 0) return stop - start;
		else return System.currentTimeMillis() - start;
	}
	
	public long getLap(){
		long start = this.start;
		if(lastLap >= 0) start = lastLap;
		if(stop >= 0) return stop - start;
		else return System.currentTimeMillis() - start;
	}
	
	public static String formatTime(long time){
		return new Date(time).toString();
	}
	
	public static String formatTimeDifference(long diff){
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
	
}
