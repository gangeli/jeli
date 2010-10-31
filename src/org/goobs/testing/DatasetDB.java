package org.goobs.testing;

import org.goobs.database.Database;

/**
 * CONSTRAINTS TO USING THIS CLASS:
 * 		~The database must have a primary key that's dense (e.g. max(key) = count(key))
 */
public class DatasetDB <D extends Datum> extends Dataset<D>{
	
	private Database db;
	private Class<D> type;
	
	private int size;
	private Datum[] cache;
	
	@Override
	public int numExamples(){ return size; }
	
	@SuppressWarnings("unchecked")
	@Override
	public D get(int id){
		if(id >= this.numExamples()){
			throw new IllegalArgumentException("ID is out of range (id=" + id + ",max=" + this.numExamples() + ")");
		}
		if(cache != null && cache[id] != null){
			return (D) cache[id];
		}
		D rtn = db.getObjectById(type, id+1);
		if(rtn == null){
			throw new IllegalArgumentException("ID " + id + " is out of range [0,"+size()+")");
		}
		rtn.setReadOnly(true);
		if(cache != null){ cache[id] = rtn; }
		return rtn;
	}
	
	public DatasetDB(Database db, Class<D> type, boolean lazy){
		//(overhead)
		if(db == null){
			throw new IllegalArgumentException("Data database is null (hint: did you forget to set it in Execution/command line?)");
		}
		this.db = db;
		this.type = type;
		//(connect)
		if(!db.isConnected()){ db.connect(); }
		this.size = db.getTableRowCount(type);
		//(init cache)
		if(!lazy){
			cache = new Datum[this.size];
		}
	}
}
