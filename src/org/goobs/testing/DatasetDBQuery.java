package org.goobs.testing;

import java.util.ArrayList;
import java.util.Iterator;

import org.goobs.database.Database;

public class DatasetDBQuery <D extends Datum> extends Dataset<D>{
	private boolean initialized = false;
	private Database db;
	private Class<D> type;
	private String query;
	
	private Iterator<D> iter = null;
	private ArrayList<Datum> cache = new ArrayList<Datum>();
	
	public DatasetDBQuery(Database db, Class<D> type, String query){
		this.db = db;
		this.type = type;
		this.query = query;
	}
	
	public void init(){
		if(initialized){ return; }
		if(!db.isConnected()){ db.connect(); }
		iter = db.getObjects(type, query);
		initialized = true;
	}
	
	@Override
	public int numExamples() {
		init();
		while(iter.hasNext()){
			cache.add( (Datum) iter.next().refreshLinks() );
		}
		return cache.size();
	}

	@SuppressWarnings("unchecked")
	@Override
	public D get(int id) {
		init();
		while(cache.size() <= id){
			cache.add( (Datum) iter.next().refreshLinks() );
		}
		return (D) cache.get(id);
	}
	
	@Override
	public Iterator<D> iterator(){
		init();
		if(!iter.hasNext()){ 
			return super.iterator(); 
		}
		return new Iterator<D>(){
			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}
			@SuppressWarnings("unchecked")
			@Override
			public D next() {
				D next = (D) iter.next().refreshLinks();
				cache.add(next);
				return next;
			}
			@Override
			public void remove() {
				iter.remove();
			}
			
		};
	}

}
