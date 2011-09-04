package org.goobs.stanford;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.*;
import org.goobs.exec.Log;
import org.goobs.testing.Dataset;
import org.goobs.utils.MetaClass;
import org.goobs.utils.Range;

import java.util.*;

public class CoreMapDataset extends Dataset<DBCoreMap> {

	private static class DependencyException extends RuntimeException {
		private DependencyException() { super(); }
		private DependencyException(String s) {
			super(s);
		}
		private DependencyException(String s, Throwable throwable) {
			super(s, throwable);
		}
		private DependencyException(Throwable throwable) {
			super(throwable);
		}
	}

	@Table(name="coremap_dataset")
	private static class Dataset extends DatabaseObject{
		@PrimaryKey(name="did")
		private int did;
		@Index
		@Key(name="name", length=127)
		private String name;
		@Key(name="creator_class")
		private Class creatorClass;
		@Key(name="maps")
		private String[] maps;
	}

	@Table(name="task")
	protected static class DatasetTask extends DatabaseObject{
 		@PrimaryKey(name="tid")
		private int tid;
		@Index
		@Key(name="name", length=127)
		private String name;
		@Index
		@Key(name="class")
		private Class<? extends Task> task;
		@Index
		@Key(name="last_run")
		private Date lastRun = new Date();

		@Child(localField="tid", childField="allows")
		private Dependency[] conditions;

		public DatasetTask(){ }
		public DatasetTask(CoreMapDataset dataset, Task t, DatasetTask[] depends){
			this.name = dataset.name + "-" +t.name();
			this.task = t.getClass();
			this.conditions = new Dependency[depends.length];
			for(int i=0; i<depends.length; i++){
				this.conditions[i] = new Dependency(this, depends[i]);
			}
		}

    @Override
    public boolean preFlush(Database db){
      if(!super.preFlush(db)){ return false; }
      if(this.conditions != null){
        for(Dependency d : this.conditions){
         db.registerObject(d);
        }
      }
			return true;
    }

		public DatasetTask perform(){
			this.lastRun = new Date();
			return this.flush();
		}
	}

	@Table(name="dependency")
	private static class Dependency extends DatabaseObject{
		@PrimaryKey(name="pid")
		private int pid;
		@Parent(localField="condition", parentField="tid")
		public DatasetTask condition;
		@Parent(localField="allows", parentField="tid")
		public  DatasetTask allows;

		private Dependency(){ }
		private Dependency(DatasetTask allows, DatasetTask condition){
			this.allows = allows;
			this.condition = condition;
		}


	}

	private Dataset dataset;
	private Database db;

	private DBCoreMap[] maps;
	private String name;;

	private CoreMapDataset(){}

	/**
	 * Constructor for reading a dataset from the database
	 * @param name The name of the dataset in the database
	 * @param db The database to read from
	 * @param lazy If true, datums are loaded on demand rather than up front
	 */
	public CoreMapDataset(String name, Database db, boolean lazy){
		this.name = name;
		//(ensure databse)
		if(!db.isConnected()){ db.connect(); }
		//(set variables)
		this.db = db;
		this.dataset = db.getObjectByKey(Dataset.class,"name",name);
		if(this.dataset == null){ throw new IllegalArgumentException("Could not find dataset: " + name); }
		this.maps = new DBCoreMap[this.dataset.maps.length];
		//(fetch if applicable)
		if(!lazy){
			for(int id=0; id<numExamples(); id++){
				get(id);
			}
		}
	}

	/**
	 * Constructor for reading a dataset from the database (load all data at construction)
	 * @param name The name of the dataset in the database
	 * @param db The database to read from
	*/
	public CoreMapDataset(String name, Database db){
		this(name,db,false);
	}

	/**
	 * Constructor for creating a dataset to be pushed to the database
	 * @param name The name of the dataset in the database
	 * @param db The database to read from
	 * @param coreMaps The DBCoreMap objects that compose the dataset.
	 * These do not yet need to be flushed
	 */
	public CoreMapDataset(String name, Database db, DBCoreMap[] coreMaps){
 		construct(name,db,coreMaps);
	}

	/**
	 * Constructor for creating a dataset to be pushed to the database
	 * @param name The name of the dataset in the database
	 * @param db The database to read from
	 * @param coreMaps The CoreMap objects that compose the dataset.
	 * Note that a separate constructor exists for DBCoreMaps
	 */
	public CoreMapDataset(String name, Database db, CoreMap[] coreMaps){
    //(create root source)
    DatasetTask rootSource = db.getObjectByKey(DatasetTask.class, "name", name+"-ROOT");
    if(rootSource == null){
      rootSource = db.emptyObject(DatasetTask.class);
      rootSource.name = name+"-ROOT";
      rootSource.task = Task.class;
      rootSource.flush();
    }
    //(convert maps)
		DBCoreMap[] data = new DBCoreMap[coreMaps.length];
		for(int i=0; i<coreMaps.length; i++){
			data[i] = db.emptyObject(DBCoreMap.class, coreMaps[i], rootSource);
		}
    //(construct dataset)
		construct(name,db,data);
	}

	private void construct(String name, Database db, DBCoreMap[] coreMaps) {
		Log.startTrack("Flushing Data");
		db.beginTransaction();
		//--Set Up
		Log.log("setup");
		this.name = name;
		this.db = db;
		this.dataset = db.getObjectByKey(Dataset.class,"name",name);
		if(this.dataset != null){ throw new IllegalArgumentException("Dataset already exists: " + name); }
		this.dataset = db.emptyObject(Dataset.class);
		this.dataset.name = name;
		//--Data
		//(flush data)
		int index = 0;
		for(DBCoreMap map : coreMaps){
			String str = map.toString();
			Log.log("flushing " + index++ + " / " + coreMaps.length + ": " + str.substring(0,Math.min(str.length(),20)));
			if(!map.isInDatabase()){
				map.deepFlush(); }

		}
		//(create dataset)
		Log.log("creating dataset");
		this.dataset.maps = new String[coreMaps.length];
		for(int i=0; i<coreMaps.length; i++){
			dataset.maps[i] = ""+coreMaps[i].eid;
			coreMaps[i].setId(i);
		}
		//(flush dataset)
		Log.log("flushing dataset");
		this.dataset.flush();
		db.endTransaction();
		//(set maps)
		this.maps = coreMaps;
		Log.end_track();
	}

	@Override
	public int numExamples() {
		return maps.length;
	}

	@Override
	public DBCoreMap get(int id) {
		if(id < 0 || id > maps.length){ throw new IllegalArgumentException("ID is out of range: " + id); }
		if(maps[id] == null){
			maps[id] = db.getObjectById(DBCoreMap.class,Integer.parseInt(dataset.maps[id]));
			if(maps[id] == null){ throw new IllegalStateException("No such map: " + dataset.maps[id]); }
		}
		maps[id].setId(id);
		return maps[id];
	}

	@Override
	public Range range() {
		return new Range(0,numExamples());
	}

	private void clearCache(){
		maps = new DBCoreMap[numExamples()];
	}

	@SuppressWarnings({"unchecked"})
  public <E extends Task> void forgetTask(E task) {
		//--Ensure No Dependencies
		for(Class<Task> depend : task.dependencies()){
			if(db.getObjectsByKey(DatasetTask.class, "class", depend) != null){
				throw new DependencyException("Trying to forget task " + task.getClass() + " with active dependency " + depend);
			}
		}
		//--Delete Task
		DatasetTask toForget = db.getObjectByKey(DatasetTask.class, "class", task.getClass());
		if(toForget != null){
			db.deleteObjectsWhere(Dependency.class, "condition='"+toForget.tid +"'");
			db.deleteObjectsWhere(Dependency.class, "allows='"+toForget.tid +"'");
			db.deleteObjectById(DatasetTask.class, toForget.tid);
		}
	}

	@SuppressWarnings({"unchecked"})
  private <E extends Task> void runTask(E task){
		//--Perform Task
    DatasetTask dbTask = db.getObjectByKey(DatasetTask.class, "class", task.getClass());
		if(dbTask == null){ throw new IllegalArgumentException("Called runTask() without creating it first"); }
		db.beginTransaction();
    //(clear previous annotation)
    db.deleteObjectsWhere(NestedElement.MapElem.class, "source='" + dbTask.tid + "'");
    db.deleteObjectsWhere(DBCoreMap.class, "source='" + dbTask.tid + "'");
    db.deleteObjectsWhere(NestedElement.ListElem.class, "source='" + dbTask.tid + "'");
    db.deleteObjectsWhere(NestedElement.DBList.class, "source='" + dbTask.tid + "'");
		clearCache();
		//(perform)
		db.endTransaction();
		task.perform(this);
		db.beginTransaction();
		//(flush result)
		for(int i=0; i<numExamples(); i++){
      this.get(i).setSource(dbTask).deepFlush();
		}
		//(update time)
		dbTask.perform();
		db.endTransaction();
		//--Run Downstream
		Iterator<Dependency> iter = db.getObjectsByKey(Dependency.class, "condition", dbTask.tid);
		while(iter.hasNext()){
			DatasetTask toPerform = ((Dependency) iter.next().refreshLinks()).allows;
			Task downstream = MetaClass.create(toPerform.task).createInstance();
			runTask(downstream);
		}
	}

	@SuppressWarnings({"unchecked"})
  public <E extends Task> CoreMapDataset runAndRegisterTask(E task){
		Class<? extends Task> taskClass = task.getClass();
		//--Create Task
		DatasetTask toCreate = db.getObjectByKey(DatasetTask.class, "class", task.getClass());
		if(toCreate != null){
			//(resolve dependency changes)
			HashSet<Class<? extends Task>> databaseOpinion = new HashSet<Class<? extends Task>>();
			HashSet<Class<? extends Task>> taskOpinion = new HashSet<Class<? extends Task>>();
			//((database's opinion of dependencies))
			toCreate.refreshLinks();
			for(Dependency cond : toCreate.conditions){
				cond.refreshLinks();
				databaseOpinion.add( cond.condition.task );
			}
			//((task's opinion on dependencies))
			for(Class<? extends Task> depend : task.dependencies()){
				taskOpinion.add(depend);
			}
			//(diff dependencies)
			//((to add))
			List<Class<? extends Task>> toAdd = new LinkedList<Class<? extends Task>>();
			for(Class<? extends Task> depend : taskOpinion){
				if(!databaseOpinion.contains(depend)){
					toAdd.add(depend);
				}
			}
			//((to remove))
			List<Class<? extends Task>> toRemove = new LinkedList<Class<? extends Task>>();
			for(Class<? extends Task> depend : databaseOpinion){
				if(!taskOpinion.contains(depend)){
					toRemove.add(depend);
				}
			}
			//(update database)
			//((add additions))
			for(Class<? extends Task> depend : toAdd){
				DatasetTask condition = db.getObjectByKey(DatasetTask.class, "task", toAdd);
				if(condition == null){ throw new DependencyException("New dependency of " + taskClass + " on " + depend + " cannot be added, since the dependency is not in the database"); }
				Dependency term = db.emptyObject(Dependency.class);
				term.allows = toCreate;
				term.condition = condition;
				term.flush();
			}
			//((remove deletions))
			for(Class<? extends Task> depend : toRemove){
				DatasetTask condition = db.getObjectByKey(DatasetTask.class, "task", toAdd);
				if(condition != null){ throw new DependencyException("New dependency of " + taskClass + " on " + depend + " cannot be removed, since the dependency is not in the database"); }
				db.deleteObjectsWhere(Dependency.class,"allows='"+toCreate.tid +"' AND condition='"+condition.tid +"'");
			}
		} else {
			//(get dependencies)
			Class<? extends Task>[] depends = task.dependencies();
			DatasetTask[] dependencies = new DatasetTask[depends.length];
			for(int i=0; i<depends.length; i++){
				dependencies[i] = db.getObjectByKey(DatasetTask.class, "class", depends[i]);
			}
			//(save object)
			db.emptyObject(DatasetTask.class,this,task,dependencies).deepFlush();
		}
		//--Run Task
		runTask(task);
		return this;
	}

	public CoreMapDataset deepCopy(){
		CoreMapDataset rtn = new CoreMapDataset();
		rtn.name = this.name;
		rtn.db = this.db;
		rtn.maps = new DBCoreMap[this.maps.length];
		for(int i=0; i<this.maps.length; i++){
			rtn.maps[i] = new DBCoreMap(this.maps[i], this.maps[i].source);
		}
		return rtn;
	}

	@Override public int hashCode(){ return numExamples(); } //don't judge :/
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("CoreMapDataset:\n");
		for(int i=0; i<this.numExamples(); i++){
			b.append("\t").append(this.get(i).toString()).append("\n");
		}
		return b.toString();
	}

	@Override
	public boolean equals(Object o){
		if(o instanceof CoreMapDataset){
			CoreMapDataset other = (CoreMapDataset) o;
			if(this.numExamples() != other.numExamples()){ return false; }
			for(int i=0; i<numExamples(); i++){
				if(!this.get(i).equals(other.get(i))){ return false; }
			}
			return true;
		}
		return false;
	}

	public static void main(String[] args){
//		Database.ConnInfo dataInfo = Database.ConnInfo.psql("localhost", "research", "what?why42?", "data");
//		Database dbData = new Database(dataInfo);
//		dbData.connect();
//		dbData.ensureTable(Dependency.class);
//		System.exit(0);


		Database.ConnInfo psql = Database.ConnInfo.psql("localhost", "java", "what?why42?", "junit");
		Database db = new Database(psql).connect();
		db.clear();

		Annotation a = new Annotation("this ( is a sample sentence. This is another sentence");
		Annotation b = new Annotation("Some more sentences. Yes, this is another sentence as well.");
		CoreMapDataset data = new CoreMapDataset("trivial", db, new CoreMap[]{ a, b });

		System.out.println("----------------------\nRunning CORE annotator");
		data.runAndRegisterTask(new JavaNLPTasks.Core());
		CoreMapDataset afterCore = data;
		System.out.println("----------------------\nRunning NER annotator");
		db.disconnect();
		db.connect();
		data = new CoreMapDataset("trivial", db);
//		if(!afterCore.equals(data)){
//			throw new IllegalStateException("Did not reload data correctly");
//		}
		CoreMapDataset beforeNER = (CoreMapDataset) data.deepCopy();
//		if(!afterCore.equals(beforeNER)){ throw new IllegalStateException("Did not deepCopy data correctly"); }
		data.runAndRegisterTask( new JavaNLPTasks.NER() );
		CoreMapDataset afterNER = data;
//    System.out.println("----------------------\nRunning CORE annotator again");
//		data.runAndRegisterTask(new JavaNLPTasks.Core());
//		System.out.println(data);

		System.out.println("----------------------\nReloading Data");
		db.disconnect();
		db.connect();
		CoreMapDataset reloaded = new CoreMapDataset("trivial", db);
//		if(!afterNER.equals(reloaded)){ throw new IllegalStateException("Did not retrieve dataset correctly after NER task"); }
		System.out.println( reloaded );
	}


}
