package org.goobs.stanford;


import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.*;
import org.goobs.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class NestedElement extends DatabaseObject {

	@PrimaryKey(name="eid")
	protected int eid;
	@Key(name="value_type")
	protected Class valueType;
	@Key(name="value", type=java.lang.String.class)
	protected Object value;

	public NestedElement(Class type, Object value){
		this.valueType = type;
		this.value = value;
	}

	protected void preFlush(Database db){
		if(value instanceof DBCoreMap){
			//--Case: DB CoreMap
			((DBCoreMap) value).deepFlush();
			value = ""+((DBCoreMap) value).eid;
		} else if(value instanceof CoreMap) {
			//--Case: CoreMap
			DBCoreMap val = db.emptyObject(DBCoreMap.class, (CoreMap) value).deepFlush();
			value = ""+val.eid;
		} else if(value instanceof DBList){
			//--Case: DB List
			((DBList) value).deepFlush();
			value = ""+((DBList) value).eid;
		} else if(value instanceof java.util.List) {
			//--Case: List
			DBList val = db.emptyObject(DBList.class, value).deepFlush();
			value = ""+val.eid;
		}
	}

	@SuppressWarnings({"unchecked"})
	public Object value(Database db){
		if(DBCoreMap.class.isAssignableFrom(this.valueType)) {
			//--Case: DB CoreMap
			if(value instanceof String){
				value = db.getObjectById(DBCoreMap.class, Integer.parseInt(value.toString()));
			}
		} else if(CoreMap.class.isAssignableFrom(this.valueType)) {
			//--Case: Regular CoreMap
			if(value instanceof String){
				value = db.getObjectById(DBCoreMap.class, Integer.parseInt(value.toString()));
			}
		} else if(DBList.class.isAssignableFrom(this.valueType)) {
			//--Case DB List
			if(value == null){ throw new IllegalStateException("Value is null but should not be: " + this); }
			if(value instanceof String){
				value = db.getObjectById(DBList.class, Integer.parseInt(value.toString())).toList(db);
			}
		} else if(java.util.List.class.isAssignableFrom(this.valueType)) {
			//--Case: Regular List
			if(value == null){ throw new IllegalStateException("Value is null but should not be: " + this); }
			if(value instanceof String){
				value = db.getObjectById(DBList.class, Integer.parseInt(value.toString())).toList(db);
			}
		} else {
			//--Case: Everything Else
			value =  Utils.cast(value.toString(), valueType);
		}
		return value;
	}



	@Table(name="list_element")
	public static class ListElem extends NestedElement implements Comparable<ListElem>{
		@Key(name="index")
		@Index
		private int index;
		@Parent(localField="parent",parentField="eid")
		private DBList parent;

		private ListElem(){ super(null,null); }
		public ListElem(int index, Object val){
			super(val.getClass(), val);
			this.index = index;
		}

		@Override public String toString(){ return "ListElement["+this.eid+" in " + parent + "]"; }
		@Override public int compareTo(ListElem listElem) { return this.index-listElem.index; }
	}

	@Table(name="list")
	public static class DBList extends NestedElement {
		@Child(localField="eid",childField="parent")
		protected ListElem[] elements;
		private List<Object> elems;

		private DBList(){ super(java.util.List.class, null); }
		@SuppressWarnings("unchecked")
		public DBList(java.util.List impl){
			super(java.util.List.class, null);
			this.elems = impl;
		}

		public java.util.List toList(Database db){
			this.refreshLinks();
			if(this.elems == null){
				Arrays.sort(elements);
				elems = new ArrayList<Object>();
				for(ListElem e : elements){
					elems.add(e.value(db));
				}
			}
			return elems;
		}

		@Override
		protected void preFlush(Database db){
			super.preFlush(db);
			//(create elements)
			this.elements = new ListElem[this.elems.size()];
			int i=0;
			for(Object o: elems){
				elements[i] = db.emptyObject(ListElem.class,i,o);
				i += 1;
			}
		}

		@Override public String toString(){ return "DBList["+this.eid+"]"; }
	}

	@Table(name="map_element")
	public static class MapElem extends NestedElement {
		@Key(name="key")
		@Index
		private Class key;
		@Parent(localField="parent",parentField="eid")
		private Map parent;

		private MapElem(){ super(null,null); }
		public MapElem(Class key, Object value){
			super(value.getClass(), value);
			this.key = key;
		}

		public Class key(){ return key; }

		@Override public String toString(){ return "MapElement["+this.eid+" in " + parent + "]"; }

	}

	@Table(name="map")
	public static abstract class Map extends NestedElement {
		@Child(localField="eid",childField="parent")
		protected MapElem[] elements;

		private Map(){ super(java.util.Map.class, null); }
		protected Map(Class valueType, Object value){
			super(valueType, value);
		}

		@Override public String toString(){ return "Map["+this.eid+"]"; }
	}

}
