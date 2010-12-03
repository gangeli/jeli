package org.goobs.database;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.goobs.database.Database.DBClassInfo;
import org.goobs.exec.Log;

public abstract class DatabaseObject {

	protected static final byte FLAG_NONE = (1<<0);
	protected static final byte FLAG_IN_DB = (1<<1);
	protected static final byte FLAG_READ_ONLY = (1<<2);
	private static final byte FLAG_FLUSHING = (byte) (1<<7);
	
	@SuppressWarnings("rawtypes")
	private static final Map<Class,Map<Database,DBClassInfo>> dbInfo 
		= new IdentityHashMap<Class,Map<Database,DBClassInfo>>();
	
	protected Database database;
	private byte flags = 0x0;
	
	private static final boolean flag(byte flags, byte flag){
		return (flag & flags) != 0;
	}
	
	private boolean isSet(byte flag){
		return flag(this.flags, flag);
	}
	
	private void setFlag(byte flag, boolean state){
		if(state){
			flags = (byte) (flags | flag);
		}else{
			flags = (byte) (flags & ~flag);
		}
	}
	
	@SuppressWarnings("rawtypes")
	protected final <E> DatabaseObject init(Database db, Class <E> clazz, Object[] args, byte flags){
		this.database = db;
		//(set flags)
		this.flags = flags;
		//(prepared statements)
		Map<Database,DBClassInfo> m = dbInfo.get(clazz);
		if(m == null){
			m = new IdentityHashMap<Database,DBClassInfo>();
			dbInfo.put(clazz,  m);
		}
		if(!m.containsKey(db)){
			if(args == null){ throw new DatabaseException("Constructor arguments needed when creating new type (forgot to call Database.registerType?)"); }
			Class<?>[] types = new Class<?>[args.length];
			for(int i=0; i<types.length; i++){
				types[i] = args[i].getClass();
			}
			m.put(db, database.createObjectInfo(this.getClass(), types));
		}
		return this;
	}
	
	@SuppressWarnings("unchecked")
	protected <E> DBClassInfo<E> getInfo(){
		return dbInfo.get(this.getClass()).get(database);
	}
	
	public boolean isInDatabase(){
		return flag(flags, FLAG_IN_DB);
	}
	protected void setInDatabase(boolean inDB){ setFlag(FLAG_IN_DB, inDB); }
	public void setReadOnly(boolean readOnly){ setFlag(FLAG_READ_ONLY, readOnly); }
	
	
	private <C extends DatabaseObject, P extends DatabaseObject>
			void backLink(P parent, C child, String childKey){
//		System.out.println(parent + "  --  " + child + "   on   " + childKey);
		//(if no child, no need to back link)
		if(child == null){ return; }
		//(for each child field...)
		for(Field f : child.getClass().getDeclaredFields()){
			//(if the field is a parent field, and matches the other side of the link)
			Parent p = f.getAnnotation(Parent.class);
			if(p != null && p.localField().equals(childKey)){
				try {
					//(set child's parent to parent)
					boolean access = f.isAccessible();
					if(!access){ f.setAccessible(true); }
					f.set(child, parent);
					if(!access){ f.setAccessible(false); }
//					System.out.println("   In class: " + child.getClass() + " set field: " + f);
				} catch (IllegalArgumentException e) {
					throw new DatabaseException(e);
				} catch (IllegalAccessException e) {
					throw new DatabaseException(e);
				}
			}
		}
		
	}
	
	@SuppressWarnings("unchecked")
	private <Me extends DatabaseObject, Other extends DatabaseObject> 
			void refreshLink(
				Field toFill,
				Class<?> parentClass,
				Class<?> childClass,
				String parentKey,
				String childKey,
				boolean parentCentric,
				boolean isArray){
		Class<Other> other = (Class<Other>) (parentCentric ? childClass : parentClass);
		//--Get Variables
		//(local pk)
		String primaryKey = null;
		int primaryKeyVal = 0;
		for(Field cand : this.getClass().getDeclaredFields()){
			PrimaryKey pk = cand.getAnnotation(PrimaryKey.class);
			if(pk != null){
				primaryKey = pk.name();
				try {
					boolean accessSave = cand.isAccessible();
					if(!accessSave){ cand.setAccessible(true); }
					primaryKeyVal = cand.getInt(this);
					if(!accessSave){ cand.setAccessible(false); }
				} catch (IllegalArgumentException e) {
					throw new DatabaseException("Primary key should be an integer for class: " + this.getClass());
				} catch (IllegalAccessException e) {
					throw Log.internal("Should always be able to access field's value");
				}
				break;
			}
		}
		if(primaryKey == null){
			throw new DatabaseException("Primary key does not exist for class (needed for foreign key lookup): " + this.getClass());
		}
		//(others)
		if(parentClass.getAnnotation(Table.class) == null){ throw new DatabaseException("No @Table annotation for parent of foreign key: " + parentClass); }
		String parentTable = parentClass.getAnnotation(Table.class).name();
		if(childClass.getAnnotation(Table.class) == null){ throw new DatabaseException("No @Table annotation for child of foreign key: " + childClass); }
		String childTable = childClass.getAnnotation(Table.class).name();
		//--Build Query
		StringBuilder b = new StringBuilder();
		b.append("SELECT ").append(parentCentric ? "child" : "parent").append(".* FROM ")
		.append(parentTable).append(" parent")
		.append(", ").append(childTable).append(" child")
		.append(" WHERE ")
		.append(parentCentric ? "parent" : "child").append(".").append(primaryKey)
		.append("='")
		.append(primaryKeyVal).append("'")
		.append(" AND ")
		.append("parent.").append(parentKey)
		.append("=")
		.append("child.").append(childKey)
		.append(";");
		//--Query
		//(run query)
		boolean accessible = toFill.isAccessible();
		if(!accessible) toFill.setAccessible(true);
		try {
			if(isArray){
				if(!parentCentric){ throw new IllegalArgumentException("Object cannot have multiple parents"); }
				//(get objects)
				Iterator<Other> iter = database.getObjects(other, b.toString());
				LinkedList<Other> lst = new LinkedList<Other>();
				while(iter.hasNext()){
					Other term = iter.next();
					backLink(this,term,childKey);
					lst.add(term);
				}
				Other[] rtn = lst.toArray((Other[]) Array.newInstance(other, lst.size()));
				if(rtn == null) throw new DatabaseException("No objects match query: " + b.toString());
				//(set)
				toFill.set(this, rtn);
			}else{
				Other rtn = null;
				if(database.hasTable(other)){
					rtn = database.getFirstObject((Class<? extends Other>) other, b.toString());
				}
				backLink(this,rtn,childKey);
				toFill.set(this, rtn);
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw new DatabaseException("Could not set foreign key field in local class: " + e);
		} catch (IllegalAccessException e) {
			throw Log.internal("Should always be able to access field's value");
		}
		if(!accessible) toFill.setAccessible(false);
	}
	
	public <D extends DatabaseObject> D refreshLinks(){
		for(Field f : this.getClass().getDeclaredFields()){
			Parent fkey = f.getAnnotation(Parent.class);
			if(fkey != null){
				//(fill objects)
				Class<?> parentClass = f.getType();
				Class<?> childClass = this.getClass();
				String parentKey = fkey.parentField();
				String childKey = fkey.localField();
				refreshLink(f, parentClass, childClass, parentKey, childKey, false, false);
			}
			Child link = f.getAnnotation(Child.class);
			if(link != null){
				if(fkey != null){ throw new DatabaseException("Invalid annotations on field: " + f); }
				Class<?> parentClass = this.getClass();
				Class<?> childClass = f.getType();
				boolean isArray = false;
				if(childClass.isArray()){ childClass = childClass.getComponentType(); isArray = true;}
				String parentKey = link.localField();
				String childKey = link.childField();
				refreshLink(f, parentClass, childClass, parentKey, childKey, true, isArray);
			}
		}
		return (D) this;
	}
	
	@SuppressWarnings("unchecked")
	public final <T extends DatabaseObject> T flush(){
		if(database == null){ 
			throw new IllegalStateException("Cannot flush a database object created with 'new'");
		}
		if(flag(flags,FLAG_READ_ONLY)){ 
			throw new IllegalStateException("Cannot flush a read-only object");
		}
		database.flush(dbInfo.get(this.getClass()).get(database), this);
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public final <T extends DatabaseObject> T deepFlush(){
		if(isSet(FLAG_FLUSHING)){ return (T) this; }
		setFlag(FLAG_FLUSHING, true);
		try {
			Class<?> clazz = this.getClass();
			for(Field f : clazz.getDeclaredFields()){
				Parent key = f.getAnnotation(Parent.class);
				if(key != null){
					DatabaseObject parent = null;
					if(f.isAccessible()) parent = (DatabaseObject) f.get(this);
					else{ f.setAccessible(true); parent = (DatabaseObject) f.get(this); f.setAccessible(false); }
					if(parent != null){ parent.deepFlush(); }
				}
			}
			flush();
			for(Field f : clazz.getDeclaredFields()){
				Child link = f.getAnnotation(Child.class);
				if(link != null && f.get(this) != null){  //moved null check up here
					boolean accessible = f.isAccessible();
					if(!accessible){ f.setAccessible(true); }
					if(f.getType().isArray()){
						DatabaseObject[] array = (DatabaseObject[]) f.get(this);
						for(DatabaseObject o : array){
							//(set foreign key)
							Field toSet = f.getType().getComponentType().getField(link.childField());
							boolean access = toSet.isAccessible();
							if(!access){ toSet.setAccessible(true); }
							toSet.set(o, this);
							if(!access){ toSet.setAccessible(false); }
							//(flush)
							o.deepFlush();
						}
					}else{
						DatabaseObject obj = (DatabaseObject) f.get(this);
						//(set foreign key)
						Field toSet = f.getType().getField(link.childField());
						boolean access = toSet.isAccessible();
						if(!access){ toSet.setAccessible(true); }
						toSet.set(obj, this);
						if(!access){ toSet.setAccessible(false); }
						//(flush)
						obj.deepFlush();
					}
					if(!accessible){ f.setAccessible(false); }
				}
			}
			setFlag(FLAG_FLUSHING, false);
			return (T) this;
		} catch (Exception e) {
			setFlag(FLAG_FLUSHING, false);
			if(e instanceof DatabaseException){ throw (DatabaseException) e; }
			throw new DatabaseException(e);
		}
	}

	
	
	
	public static final Date now(){
		return new Date(System.currentTimeMillis());
	}

}
