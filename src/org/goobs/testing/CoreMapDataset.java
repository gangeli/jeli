package org.goobs.testing;


import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.*;
import org.goobs.utils.Range;
import org.goobs.utils.Utils;

import java.util.*;

public class CoreMapDataset extends Dataset<Datum>{
	private Database db;

	private class Element<VAL> extends DatabaseObject {
		private Class<VAL> valueType;
		private String value;

		public VAL value(){
			if(CoreMap.class.isAssignableFrom(valueType)){
				//--Case: CoreMap
				//(get items)
				return (VAL) db.getObjectByKey(DBCoreMap.class, "mid", value);
			} else if(List.class.isAssignableFrom(valueType) || valueType.isArray()){
				//--Case: Ordered collection of something
				//(get items)
				Iterator<ListElement> iter = db.getObjectsByKey(ListElement.class, "parent", value);
				//(collect items)
				HashMap<Integer,Object> map = new HashMap<Integer,Object>();
				while(iter.hasNext()){
					ListElement item = iter.next();
					if(item.index < 0){ throw new IllegalStateException("Element is not a list elem: " + item); }
					map.put(item.index, item.value());
				}
				//(sort items)
				Object[] sorted = new Object[map.keySet().size()];
				for(Integer index : map.keySet()){
					sorted[index] = map.get(index);
				}
				//(cast to return type)
				if(List.class.isAssignableFrom(valueType)){
					return (VAL) Arrays.asList(sorted);
				}else if(valueType.isArray()){
					return (VAL) sorted;
				} else {
					throw new IllegalStateException("Unknown list type to cast to: " + valueType);
				}
			} else {
				//--Case: Castable
				return (VAL) Utils.cast(value,valueType);
			}
		}
	}

	private class ListElement<VAL> extends Element {
		private int index;
		private int parent;
	}

	private class MapElement<KEY,VAL> extends Element {
		private int mid;
		private Class<KEY> key;
		public Class<KEY> key(){ return key; }

	}

	private class DBCoreMap extends Datum implements CoreMap {

		@PrimaryKey(name="mid")
		private int mid;
		@org.goobs.database.Key(name="index")
		private int index;
		@Child(localField="mid",childField="mid")
		private MapElement[] elements;

		private CoreMap impl = null;

		private void ensureInitialized(){
			if(impl == null){
				//(ensure valid state)
				this.refreshLinks();
				impl = new ArrayCoreMap(0);
				//(populate map)
				for(MapElement elem : elements){
					impl.set(elem.key(),elem.value());
				}
			}
		}

		@Override
		public <VALUE, KEY extends Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) {
			ensureInitialized();
			return impl.has(keyClass);
		}
		@Override
		public <VALUE, KEY extends Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) {
			ensureInitialized();
			return impl.get(keyClass);
		}
		@Override
		public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) {
			ensureInitialized();
			return impl.set(keyClass, value);
		}
		@Override
		public <VALUE, KEY extends Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) {
			ensureInitialized();
			return impl.remove(keyClass);
		}
		@Override
		public Set<Class<?>> keySet() {
			ensureInitialized();
			return impl.keySet();
		}
		@Override
		public <VALUE, KEY extends Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) {
			ensureInitialized();
			return impl.containsKey(keyClass);
		}
		@Override
		public int size() {
			ensureInitialized();
			return impl.size();
		}
		@Override
		public int getID() {
			return index;
		}
	}
























	@Override
	public int numExamples() {
		return 0;	//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Datum get(int id) {
		return null;	//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public Range range() {
		return null;	//To change body of implemented methods use File | Settings | File Templates.
	}

	public static void main(String[] args){
		System.out.println("Hello World");
	}

}
