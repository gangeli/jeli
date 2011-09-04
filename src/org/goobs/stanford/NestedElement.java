package org.goobs.stanford;


import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;
import org.goobs.database.*;
import org.goobs.utils.Utils;

import java.util.*;

public abstract class NestedElement extends DatabaseObject {

	@PrimaryKey(name="eid")
	protected int eid;
	@Key(name="value_type")
	protected Class valueType;
	@Key(name="value", type=java.lang.String.class)
	private Object value;
  @Parent(localField="source", parentField="tid")
  protected CoreMapDataset.DatasetTask source;

	protected boolean changed;

	public NestedElement(Class type, Object value, CoreMapDataset.DatasetTask task){
		this.valueType = type;
		this.value = value;
    this.source = task;
		this.changed = !this.isInDatabase();
	}

	protected boolean preFlush(Database db){
		if(this.isInDatabase() && !changed){ return false; }
		if(value instanceof DBCoreMap){
			//--Case: DB CoreMap
			((DBCoreMap) value).deepFlush();
			value = ""+((DBCoreMap) value).eid;
		} else if(value instanceof CoreMap) {
			//--Case: CoreMap
			db.registerType(DBCoreMap.class, DBCoreMap.class, CoreMapDataset.DatasetTask.class);
			DBCoreMap val = db.emptyObject(DBCoreMap.class, value, source).deepFlush();
			value = ""+val.eid;
		} else if(value instanceof DBList){
			//--Case: DB List
			((DBList) value).deepFlush();
			value = ""+((DBList) value).eid;
		} else if(value instanceof java.util.List) {
			//--Case: List
			db.registerType(DBList.class, java.util.List.class, CoreMapDataset.DatasetTask.class);
			DBList val = db.emptyObject(DBList.class, value, source).deepFlush();
			value = ""+val.eid;
		} else if(value  instanceof Calendar){
			value = ""+((Calendar) value).getTimeInMillis();
		}
		return true;
	}

	protected void postFlush(Database db){
		this.changed = false;
	}

	@SuppressWarnings({"unchecked"})
	public Object value(Database db){
		if(DBCoreMap.class.isAssignableFrom(this.valueType)) {
			//--Case: DB CoreMap
			if(value instanceof String){
				value = db.getObjectById(DBCoreMap.class, Integer.parseInt(value.toString()));
			}
		} else if(CoreLabel.class.isAssignableFrom(this.valueType)){
			//--Case: CoreLabel
			if(value instanceof String){
				value = new MyCoreLabel( db.getObjectById(DBCoreMap.class, Integer.parseInt(value.toString())) );
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
				DBList lst = db.getObjectById(DBList.class, Integer.parseInt(value.toString()));
				if(lst == null){ throw new IllegalStateException("No such list: " + value + ":: " + this.getClass() + ":: " + this.eid); }
				value = lst.toList(db);
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
		this.changed = true;
		return value;
	}

	protected void setValue(Object obj){
		this.value = obj;
		this.changed = true;
	}







	@Table(name="list_element")
	public static class ListElem extends NestedElement implements Comparable<ListElem>{
		@Key(name="index")
		@Index
		private int index;
		@Parent(localField="parent",parentField="eid")
		private DBList parent;

		private ListElem(){ super(null,null,null); }
		public ListElem(int index, Object val, CoreMapDataset.DatasetTask task){
			super(val.getClass(), val, task);
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

		private DBList(){ super(java.util.List.class, null, null); }
		@SuppressWarnings("unchecked")
		public DBList(java.util.List impl, CoreMapDataset.DatasetTask task){
			super(java.util.List.class, null, task);
			this.elems = impl;
		}

		public java.util.List toList(Database db){
			if(this.elems == null){
				this.refreshLinks();
				Arrays.sort(elements);
				elems = new ArrayList<Object>();
				for(ListElem e : elements){
					elems.add(e.value(db));
				}
			}
			this.changed = true;
			return elems;
		}

		@Override
		protected boolean preFlush(Database db){
			if(!super.preFlush(db)){ return false; }
			if(this.elems != null && !this.isInDatabase()){
				//(create elements)
				this.elements = new ListElem[this.elems.size()];
				int i=0;
				for(Object o: elems){
					elements[i] = db.emptyObject(ListElem.class,i,o, this.source);
					i += 1;
				}
				if(this.isInDatabase()){ throw new IllegalStateException("For now, lists are immutable"); }
			} else if(this.elems != null && this.elements.length != this.elems.size()){
				//(mutable list) //TODO mutable lists
				throw new IllegalStateException("Lists are immutable (for now)!");
			}
			return true;
		}

		@Override public String toString(){ return "DBList["+this.eid+"]"; }
	}








	@Table(name="map_element")
	public static class MapElem extends NestedElement {
		@Key(name="key")
		@Index
		private Class key;
		@Parent(localField="parent",parentField="eid")
		private DBCoreMap parent;

		private MapElem(){ super(null,null,null); }
		public MapElem(Class key, Object value, CoreMapDataset.DatasetTask task){
			super(value.getClass(), value, task);
			this.key = key;
		}

		public Class key(){ return key; }

		@Override public String toString(){ return "MapElement["+this.eid+" in " + parent + "]"; }

	}



	protected static class MyAnnotation extends Annotation {
		private CoreMap impl;

		@SuppressWarnings({"deprecation"})
    public MyAnnotation(CoreMap impl){ super(); this.impl = impl; }

		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) { return impl.has(keyClass); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) { return impl.get(keyClass); }
		@Override public <VALUEBASE, VALUE extends VALUEBASE, KEY extends TypesafeMap.Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) {
			if(value instanceof java.util.List && this.has(keyClass)){ return null; } //TODO hack
			return impl.set(keyClass, value);
		}
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) { return impl.remove(keyClass); }
		@Override public Set<Class<?>> keySet() { return impl.keySet(); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) { return impl.containsKey(keyClass); }
		@Override public int size() { return impl.size(); }
		@Override public String toString() { return impl.toString();}
  	@Override public boolean equals(Object obj) { return impl.equals(obj); }
		@Override public int hashCode(){ return impl.hashCode(); }
	}

	protected static class MyCoreLabel extends CoreLabel{
		private CoreMap impl;

		public MyCoreLabel(CoreMap impl) {
			this.impl = impl;
		}
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) { return impl.has(keyClass); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) { return impl.get(keyClass); }
		@Override public <VALUEBASE, VALUE extends VALUEBASE, KEY extends TypesafeMap.Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) { return impl.set(keyClass, value); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) { return impl.remove(keyClass); }
		@Override public Set<Class<?>> keySet() { return impl.keySet(); }
		@Override public <VALUE, KEY extends TypesafeMap.Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) { return impl.containsKey(keyClass); }
		@Override public int size() { return impl.size(); }
		@Override public String toString() { return impl.toString();}
  	@Override public boolean equals(Object obj) { return impl.equals(obj); }
		@Override public int hashCode(){ return impl.hashCode(); }
	}
}
