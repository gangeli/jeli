package org.goobs.testing;

import org.goobs.database.DatabaseObject;
import org.goobs.database.PrimaryKey;


public abstract class Datum extends DatabaseObject{
	public abstract int getID();
	
	@Override
	public boolean equals(Object o){
		if(o instanceof Datum){
			return ((Datum) o).getID() == this.getID();
		}else{
			return false;
		}
	}
	@Override
	public int hashCode(){ return getID(); }
	@Override
	public String toString(){ return "Datum-"+getID(); }
}
