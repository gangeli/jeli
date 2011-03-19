package org.goobs.testing;

import java.util.HashMap;

import org.goobs.exec.Log;

public class InMemoryLogger extends ResultLogger {

	public final HashMap<String,String> globalResults = new HashMap<String,String>();
	public final HashMap<Integer,HashMap<String,String>> localResults = new HashMap<Integer,HashMap<String,String>>();
	
	public final HashMap<Integer,InMemoryLogger> groups = new HashMap<Integer, InMemoryLogger>();
	
	@Override
	public String getIndexPath() {
		throw new NoSuchMethodError();
	}
	@Override
	public String getPath() {
		throw new NoSuchMethodError();
	}

	@Override
	public void setGlobalResult(String name, double value) {
		globalResults.put(name, ""+value);
	}

	@Override
	public void addGlobalString(String name, Object value) {
		globalResults.put(name, value.toString());
	}

	@Override
	public void setLocalResult(int index, String name, double value) {
		if(!localResults.containsKey(index)){
			localResults.put(index, new HashMap<String,String>());
		}
		localResults.get(index).put(name,""+value);
	}

	@Override
	public void addLocalString(int index, String name, Object value) {
		if(!localResults.containsKey(index)){
			localResults.put(index, new HashMap<String,String>());
		}
		localResults.get(index).put(name,value.toString());
	}

	@Override
	public void add(int index, Object guess, Object gold) {
		if(!localResults.containsKey(index)){
			localResults.put(index, new HashMap<String,String>());
		}
		localResults.get(index).put("guess",guess.toString());
		localResults.get(index).put("gold",gold.toString());
	}

	@Override
	public void save(String root, boolean index) { 
		Log.warn("InMemoryLogger", "Cannot save in-memory logger");
	}

	@Override
	public ResultLogger spawnGroup(String name, int index) {
		InMemoryLogger child = new InMemoryLogger();
		this.groups.put(index,child);
		return child;
	}

	@Override
	public void suggestFlush() {}
	
	public void clear(){
		this.globalResults.clear();
		this.localResults.clear();
		this.groups.clear();
	}

}
