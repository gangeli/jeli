package org.goobs.testing;

import org.goobs.database.DatabaseObject;
import org.goobs.database.PrimaryKey;


public abstract class Datum extends DatabaseObject{
	@PrimaryKey(name="id")
	protected int id;
	
	public int getID(){ return id; }
	
	@Override
	public boolean equals(Object o){
		if(o instanceof Datum){
			return ((Datum) o).id == this.id;
		}else{
			return false;
		}
	}
	@Override
	public int hashCode(){ return id; }
	@Override
	public String toString(){ return "Datum-"+id; }
}
