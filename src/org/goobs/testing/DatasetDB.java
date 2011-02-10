package org.goobs.testing;

import org.goobs.database.Database;
import org.goobs.utils.Range;

/**
 * IMPORTANT: The database must have a primary key that's dense (e.g. max(key) = count(key))
 */
public class DatasetDB <D extends Datum> extends Dataset<D>{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1466186009554812764L;
	private Database db;
	private Class<D> type;
	
	private int size;
	private Datum[] cache;
	
	private Range range;
	
	public DatasetDB(Database db, Class<D> type, boolean lazy){
		//(overhead)
		if(db == null){
			throw new IllegalArgumentException("Data database is null (hint: did you forget to set it in Execution/command line?)");
		}
		this.db = db;
		this.type = type;
		//(connect)
		if(!db.isConnected()){ db.connect(); }
		//(sizes)
		this.size = db.getTableRowCount(type);
		this.range = new Range(db.min(type),db.max(type)+1);
		//(init cache)
		if(!lazy){
			cache = new Datum[range.length()];
		}
		//(error checks)
		if(this.size != this.range.length()){
			throw new IllegalStateException("Database indices are not continuously numbered!");
		}
	}

	@Override
	public int numExamples(){ return this.size; }
	
	@Override
	public Range range() { return this.range; }
	
	@SuppressWarnings("unchecked")
	@Override
	public D get(int id){
		if(!this.range.inRange(id)){
			throw new IllegalArgumentException("ID is out of range (id=" + id + ",range=" + this.range +")");
		}
		int cacheIndex = this.range.toCacheIndex(id);
		if(cache != null && cache[cacheIndex] != null){
			return (D) cache[cacheIndex];
		}
		D rtn = db.getObjectById(type, id);
		if(rtn == null){
			throw new IllegalArgumentException("ID " + id + " is out of range [0,"+size()+")");
		}
		rtn.setReadOnly(true);
		if(cache != null){ cache[cacheIndex] = rtn; }
		rtn.refreshLinks();
		return rtn;
	}
}
