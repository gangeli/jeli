package org.goobs.testing;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.goobs.database.*;
import static org.goobs.exec.Log.*;
import org.goobs.utils.SparseList;

public class DBResultLogger extends ResultLogger{

	/*
	 * SCHEMA
	 */
	@SuppressWarnings("unused")
	@Table(name="RUN")
	private static final class Run extends DatabaseObject{
		@PrimaryKey(name="rid", autoIncrement=true)
		private int rid;
		@Key(name="name", length=63)@Index(type=Index.Type.HASH)
		private String name;
		@Key(name="completed")
		private boolean completed = false;
		@Key(name="start")@Index(type=Index.Type.BTREE)
		private Date start = new Date(System.currentTimeMillis());
		@Key(name="stop")@Index(type=Index.Type.BTREE)
		private Date stop = new Date(0L);
		private Run(String name){
			this.name = name;
		}
		@Key(name="parent")@Index(type=Index.Type.HASH)
		private int parent = -1;
		private void complete(){
			completed = true;
			this.stop = new Date(System.currentTimeMillis());
			this.flush();
		}
	}
	@SuppressWarnings("unused")
	@Table(name="PARAM")
	private static final class Param extends DatabaseObject{
		@PrimaryKey(name="pid", autoIncrement=true)
		private int pid;
		@Parent(localField="rid", parentField = "rid")
		private Run rid;
		@Key(name="key", length=63)@Index(type=Index.Type.HASH)
		private String key;
		@Key(name="value", length=255)
		private String value;
		public Param(Run result, String key, String value){
			this.rid = result;
			this.key = key;
			this.value = value;
		}
	}
	@SuppressWarnings("unused")
	@Table(name="OPTION")
	private static final class Option extends DatabaseObject{
		@PrimaryKey(name="oid", autoIncrement=true)
		private int oid;
		@Parent(localField="rid", parentField = "rid")
		private Run rid;
		@Key(name="key", length=63)@Index(type=Index.Type.HASH)
		private String key;
		@Key(name="value", length=255)
		private String value;
		@Key(name="location", length=255)
		private String location;
		public Option(Run result, String key, String value, String location){
			this.rid = result;
			this.key = key;
			this.value = value;
			this.location = location;
		}
	}
	@SuppressWarnings("unused")
	@Table(name="GLOBAL_RESULT")
	private static final class GlobalResult extends DatabaseObject{
		@PrimaryKey(name="gid", autoIncrement=true)
		private int gid;
		@Key(name="key", length=63)@Index(type=Index.Type.HASH)
		private String key;
		@Key(name="value", length=127)
		private String value;
		@Parent(localField="rid", parentField = "rid")
		private Run rid;
		private GlobalResult(Run run, String key, String value){
			this.rid = run;
			this.key = key;
			this.value = value;
		}
	}
	@SuppressWarnings("unused")
	@Table(name="EXAMPLE")
	private static final class Instance extends DatabaseObject{
		@PrimaryKey(name="eid", autoIncrement=true)
		private int eid;
		@Key(name="example_index")
		private int exampleIndex;
		@Key(name="guess", length=-1)
		@Index(type=Index.Type.HASH)
		private String guess;
		@Key(name="gold", length=-1)
		@Index(type=Index.Type.HASH)
		private String gold;
		private Instance(int index, String guess, String gold){
			this.exampleIndex = index;
			this.guess = guess;
			this.gold = gold;
		}
	}
	@SuppressWarnings("unused")
	@Table(name="LOCAL_RESULT")
	private static final class LocalResult extends DatabaseObject{
		@PrimaryKey(name="lid", autoIncrement=true)
		private int lid;
		@Key(name="key", length=63)@Index(type=Index.Type.HASH)
		private String key;
		@Key(name="value", length=127)
		private String value;
		@Parent(localField="eid", parentField = "eid")
		private Instance eid;
		private LocalResult(Instance instance, String key, String value){
			this.eid = instance;
			this.key = key;
			this.value = value;
		}
	}
	
	/*
	 * VARIABLES
	 */
	private Database db;
	private Run run;
	private SparseList<Instance> instances = new SparseList<Instance>();
	private Queue<DatabaseObject> toFlush = new LinkedList<DatabaseObject>();
	
	private Lock flushQueueLock = new ReentrantLock();
	private static Lock flushingLock = new ReentrantLock();
	
	private enum State { SIMPLE, META, NONE }
	private State state;
	
	private HashMap<String,HashMap<Integer,ResultLogger>> groups
		= new HashMap<String,HashMap<Integer,ResultLogger>>();
	
	
	
	public DBResultLogger(Database db, String runName){
		this.db = db;
		if(!db.isConnected()){ db.connect(); }
		this.run = db.emptyObject(Run.class, runName == null ? "(none)" : runName);
		run.flush();
	}
	
	/*
	 * PRE-LOGGING CODE
	 */
	
	public void logOption(String name, String value, String location){
		try{
			db.emptyObject(Option.class, run, name, value, location).flush();
		} catch (DatabaseException e){
			warn("DB_LOGGER", "Could not log option: " + name + " (" + e.getMessage() + ")");
		}
	}
	
	public void logParameter(String name, String value){
		try{
			db.emptyObject(Param.class, run, name, value).flush();
		} catch (DatabaseException e){
			warn("DB_LOGGER", "Could not log param: " + name + " (" + e.getMessage() + ")");
		}
	}

	public int runIndex(){
		return this.run.rid;
	}
	
	/*
	 * LOGGING CODE
	 */
	
	private void offer(DatabaseObject o){
		flushQueueLock.lock();
		toFlush.offer(o);
		flushQueueLock.unlock();
	}
	
	private void enforceSimple(){
		if(this.state == State.META){
			throw new IllegalStateException("Cannot log local results (or add examples) to a logger which has spawned groups (is not a leaf node)");
		}
		this.state = State.SIMPLE;
	}

	@Override
	public void add(int index, Object guess, Object gold) {
		//(overhead)
		if(guess.getClass().isEnum() || gold.getClass().isEnum()){
			guess = guess.toString();
			gold = gold.toString();
		}
		enforceSimple();
		//(add instance)
		Instance i = db.emptyObject(Instance.class, index, guess, gold);
		if(instances.get(index) != null){ fail("Duplicate example index: " + index); }
		offer(i);
		instances.set(index, i);
	}

	@Override
	public void addGlobalString(String name, Object value) {
		offer( db.emptyObject(GlobalResult.class, run, name, value.toString()) );	
	}

	@Override
	public void addLocalString(int index, String name, Object value) {
		enforceSimple();
		Instance i = instances.get(index);
		if(i == null){
			throw new IllegalArgumentException("Must add an instance before setting it's results");
		}
		offer( db.emptyObject(LocalResult.class, i, name, value.toString()) );
	}

	@Override
	public String getIndexPath() {
		return db.toString();
	}

	@Override
	public String getPath() {
		return db.toString();
	}

	@Override
	public void save(String root, boolean index) {
		//--Flush Children (if applicable)
		if(state == State.META){
			for(String group : this.groups.keySet()){
				HashMap<Integer,ResultLogger> elems = this.groups.get(group);
				for(Integer i : elems.keySet()){
					elems.get(i).save();
				}
			}
		}
		//--Flush This
		//(create separate threadspace)
		flushQueueLock.lock();
		Queue<DatabaseObject> queue = toFlush;
		toFlush = new LinkedList<DatabaseObject>();
		flushQueueLock.unlock();
		//(flush results)
		flushingLock.lock(); //must be before beginTransaction
		db.beginTransaction();
		for(DatabaseObject o : queue){
			o.flush();
		}
		db.endTranaction();
		flushingLock.unlock();
		//(mark as done)
		this.run.complete();
		//(remind user of run)
		log("THIS IS RUN #" + this.run.rid);
	}

	@Override
	public void setGlobalResult(String name, double value) {
		this.addGlobalString(name, "" + value);
	}
	@Override
	public void setLocalResult(int index, String name, double value) {
		enforceSimple();
		this.addLocalString(index, name, "" + value);
	}

	@Override
	public ResultLogger spawnGroup(String name, int index) {
		//(ensure state)
		if(this.state == State.SIMPLE){
			throw new IllegalStateException("Cannot spawn a group once a process has begun logging examples (i.e. this logger is a leaf node)");
		}
		this.state = State.META;
		//(get group)
		HashMap<Integer,ResultLogger> elems = this.groups.get(name);
		if(elems == null){
			elems = new HashMap<Integer,ResultLogger>();
			this.groups.put(name,elems);
		}
		//(add logger)
		DBResultLogger rtn = new DBResultLogger(this.db, this.run.name + "-" + name + index);
		rtn.run.parent = this.run.rid;
		rtn.run.flush();
		if( elems.put(index,rtn) != null){
			warn("DATABASE_LOGGER", "Clobbering logger in group " + name + " with index " + index);
		}
		return rtn;
	}

	@Override
	public void suggestFlush() {
		new Thread(){
			@Override
			public void run(){
				DBResultLogger.this.save();
			}
		}.start();
	}
}
