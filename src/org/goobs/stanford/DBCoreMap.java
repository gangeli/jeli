package org.goobs.stanford;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.Database;
import org.goobs.database.DatabaseException;
import org.goobs.testing.Datum;

import java.util.ArrayList;
import java.util.Set;


public class DBCoreMap extends NestedElement.Map implements CoreMap, Datum {

	private CoreMap impl;

	private int id = -1;


	/**
		 * Copy constructor.
		 *
		 * @param map The new Annotation copies this one.
		 */
		public DBCoreMap(CoreMap map) {
			super(CoreMap.class, null);
			impl = new ArrayCoreMap(map);
		}

		/**
		 * The text becomes the CoreAnnotations.TextAnnotation of the newly
		 * created Annotation.
		 * @param text The text to be annotated
		 */
		public DBCoreMap(String text) {
			super(CoreMap.class, null);
			impl = new ArrayCoreMap(1);
			impl.set(CoreAnnotations.TextAnnotation.class, text);
		}

	private DBCoreMap(){ super(null,null); }

	protected void setId(int id){
		this.id = id;
	}

	@SuppressWarnings({"unchecked"})
	private void updateMap() {
		if (impl == null) {
			//(error check)
			if(this.database == null){
				throw new DatabaseException("CoreMap is not in database!");
			}
			//(ensure valid state)
			this.refreshLinks();
			impl = new ArrayCoreMap(0);
			//(populate map)
			for (NestedElement.MapElem elem : elements) {
				impl.set(elem.key(), elem.value(this.database));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void updateElements(Database db){
		if(this.elements == null && this.impl == null){ throw new IllegalStateException("flushing but was never initialized! (forgot refreshLinks()?)"); }
		else if(this.impl != null){
			ArrayList<Object> elems = new ArrayList<Object>();
			for(Class key : impl.keySet()){
				Object val = impl.get(key);
				if(val instanceof CoreMap) {
					val = db.emptyObject(DBCoreMap.class, (CoreMap) val);
				} else if(val instanceof java.util.List) {
					val = db.emptyObject(DBList.class, val);
				}
				elems.add( db.emptyObject(NestedElement.MapElem.class,key,val) );
			}
			this.elements = elems.toArray(new NestedElement.MapElem[elems.size()]);
		}
	}

	@Override
	protected void preFlush(Database db){
		super.preFlush(db);
		updateElements(db);
	}

	@Override
	public <VALUE, KEY extends Key<CoreMap, VALUE>> boolean has(Class<KEY> keyClass) {
		this.updateMap();
		return impl.has(keyClass);
	}

	@Override
	public <VALUE, KEY extends Key<CoreMap, VALUE>> VALUE get(Class<KEY> keyClass) {
		this.updateMap();
		return impl.get(keyClass);
	}

	@Override
	public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<CoreMap, VALUEBASE>> VALUE set(Class<KEY> keyClass, VALUE value) {
		this.updateMap();
		return impl.set(keyClass, value);
	}

	@Override
	public <VALUE, KEY extends Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) {
		this.updateMap();
		return impl.remove(keyClass);
	}

	@Override
	public Set<Class<?>> keySet() {
		this.updateMap();
		return impl.keySet();
	}

	@Override
	public <VALUE, KEY extends Key<CoreMap, VALUE>> boolean containsKey(Class<KEY> keyClass) {
		this.updateMap();
		return impl.containsKey(keyClass);
	}

	@Override
	public int size() {
		this.updateMap();
		return impl.size();
	}

	@Override
	public String toString() {
    this.updateMap();
		return impl.toString();
  }

	@Override
	public int getID() {
		if(this.id < 0){ throw new IllegalStateException("This is not a top-level CoreMap"); }
		return this.id;
	}

  @Override
  public boolean equals(Object obj) {
		this.updateMap();
		return impl.equals(obj);
  }

	@Override
	public int hashCode(){
		this.updateMap();
		return impl.hashCode();
	}

}
