package org.goobs.stanford;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.Child;
import org.goobs.database.Database;
import org.goobs.database.DatabaseException;
import org.goobs.database.Table;
import org.goobs.testing.Datum;

import javax.swing.text.html.parser.Element;
import java.util.*;

/*
	TODO
		- should be able to add to list; currently it does not recognize that the list has changed
		- clean up old annotations when they have been updated
 */

@Table(name="map")
public class DBCoreMap extends NestedElement implements CoreMap, Datum {

	@Child(localField="eid",childField="parent")
	protected MapElem[] elements;

	private static HashMap<Long,DBCoreMap> updateMarker = new HashMap<Long,DBCoreMap>();

	private CoreMap impl;
  private Set<Class> added = new HashSet<Class>();
  private Set<Class> removed = new HashSet<Class>();

	private int id = -1;


	/**
		 * Copy constructor.
		 *
		 * @param map The new Annotation copies this one.
		 */
		public DBCoreMap(CoreMap map, CoreMapDataset.DatasetTask source) {
			super(CoreMap.class, null, source);
			impl = new ArrayCoreMap(map);
			for(Class key : impl.keySet()){
				added.add(key);
			}
		}

		/**
		 * The text becomes the CoreAnnotations.TextAnnotation of the newly
		 * created Annotation.
		 * @param text The text to be annotated
		 */
		public DBCoreMap(String text,CoreMapDataset.DatasetTask source) {
			super(CoreMap.class, null,source);
			impl = new ArrayCoreMap(1);
			this.set(CoreAnnotations.TextAnnotation.class, text);
		}

	private DBCoreMap(){ super(null,null, null); }

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
			//(ensure transaction)
			if(updateMarker.get(Thread.currentThread().getId()) == null){
				updateMarker.put(Thread.currentThread().getId(), this);
				this.database.beginTransaction();
			}
			//(ensure valid state)
			if(this.elements == null){
				this.refreshLinks();
			}
			//(populate map)
			impl = new ArrayCoreMap(0);
			for (NestedElement.MapElem elem : elements) {
				elem.refreshLinks();
				impl.set(elem.key(), elem.value(this.database));
			}
			if(updateMarker.get(Thread.currentThread().getId()) == this){
				this.database.endTransaction();
				updateMarker.remove(Thread.currentThread().getId());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private boolean updateElements(Database db){
		if(this.elements == null && this.impl == null){ throw new IllegalStateException("flushing but was never initialized! (forgot refreshLinks()?)"); }
		else if(this.impl != null){
			ArrayList<Object> elems = new ArrayList<Object>();
      if(this.elements != null){
      	for(MapElem elem : this.elements){
					if(!this.removed.contains(elem.key()) && !added.contains(elem.key())){
						elems.add(elem);
						added.remove(elem.key());
						removed.remove(elem.key());
					}
				}
      }
			Iterator<Class> iter = added.iterator();
			while(iter.hasNext()){
				Class key = iter.next();
				Object val = impl.get(key);
				if(val instanceof CoreMap) {
					val = db.emptyObject(DBCoreMap.class, (CoreMap) val, this.source);
				} else if(val instanceof java.util.List) {
					val = db.emptyObject(DBList.class, val, this.source);
				}
				elems.add( db.emptyObject(NestedElement.MapElem.class,key,val,this.source) );
				iter.remove();
			}
			this.elements = elems.toArray(new NestedElement.MapElem[elems.size()]);
			return true;
		} else {
			return false;
		}
	}

  public DBCoreMap setSource(CoreMapDataset.DatasetTask task){
    this.source = task;
    return this;
  }

	@Override
	protected boolean preFlush(Database db){
		return super.preFlush(db) && updateElements(db);
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
    added.add(keyClass);
		this.changed = true;
		return impl.set(keyClass, value);
	}

	@Override
	public <VALUE, KEY extends Key<CoreMap, VALUE>> VALUE remove(Class<KEY> keyClass) {
		this.updateMap();
    removed.add(keyClass);
		this.changed = true;
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
