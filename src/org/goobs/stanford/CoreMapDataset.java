package org.goobs.stanford;


import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.*;
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
	private static class DatasetTask extends DatabaseObject{
 		@PrimaryKey(name="aid")
		private int aid;
		@Index
		@Key(name="name", length=127)
		private String name;
		@Index
		@Key(name="class")
		private Class<? extends Task> task;
		@Index
		@Key(name="last_run")
		private Date lastRun = new Date();

		@Child(localField="aid", childField="condition")
		private Dependency[] conditions;

		public DatasetTask(){ }
		public DatasetTask(Task t, DatasetTask[] depends){
			this.name = t.name();
			this.task = t.getClass();
			this.conditions = new Dependency[depends.length];
			for(int i=0; i<depends.length; i++){
				this.conditions[i] = new Dependency(this, depends[i]);
			}

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
		@Parent(localField="condition", parentField="aid")
		private DatasetTask condition;
		@Parent(localField="allows", parentField="aid")
		private DatasetTask allows;

		public Dependency(){ }
		public Dependency(DatasetTask allows, DatasetTask condition){
			this.allows = allows;
			this.condition = condition;
		}
	}

	private Dataset dataset;
	private Database db;

	private DBCoreMap[] maps;

	/**
	 * Constructor for reading a dataset from the database
	 * @param name The name of the dataset in the database
	 * @param db The database to read from
	 * @param lazy If true, datums are loaded on demand rather than up front
	 */
	public CoreMapDataset(String name, Database db, boolean lazy){
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
		DBCoreMap[] data = new DBCoreMap[coreMaps.length];
		for(int i=0; i<coreMaps.length; i++){
			data[i] = db.emptyObject(DBCoreMap.class, coreMaps[i]);
		}
		construct(name,db,data);
	}

	private void construct(String name, Database db, DBCoreMap[] coreMaps) {
		//--Set Up
		this.db = db;
		this.dataset = db.getObjectByKey(Dataset.class,"name",name);
		if(this.dataset != null){ throw new IllegalArgumentException("Dataset already exists: " + name); }
		this.dataset = db.emptyObject(Dataset.class);
		this.dataset.name = name;
		//--Data
		//(flush data)
		for(DBCoreMap map : coreMaps){
			if(!map.isInDatabase()){ map.deepFlush(); }
		}
		//(create dataset)
		this.dataset.maps = new String[coreMaps.length];
		for(int i=0; i<coreMaps.length; i++){
			dataset.maps[i] = ""+coreMaps[i].eid;
			coreMaps[i].setId(i);
		}
		//(flush dataset)
		this.dataset.flush();
		//(set maps)
		this.maps = coreMaps;
	}

	@Override
	public int numExamples() {
		return dataset.maps.length;
	}

	@Override
	public DBCoreMap get(int id) {
		if(id < 0 || id > maps.length){ throw new IllegalArgumentException("ID is out of range: " + id); }
		if(maps[id] == null){
			maps[id] = db.getObjectById(DBCoreMap.class,Integer.parseInt(dataset.maps[id])).refreshLinks();
		}
		maps[id].setId(id);
		return maps[id];
	}

	@Override
	public Range range() {
		return new Range(0,numExamples());
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
			db.deleteObjectsWhere(Dependency.class, "condition='"+toForget.aid+"'");
			db.deleteObjectsWhere(Dependency.class, "allows='"+toForget.aid+"'");
			db.deleteObjectById(DatasetTask.class, toForget.aid);
		}
	}

	@SuppressWarnings({"unchecked"})
  private <E extends Task> void runTask(E task){
		//--Perform Task
		db.beginTransaction();
		//(perform)
		task.perform(this);
		//(flush result)
		for(int i=0; i<numExamples(); i++){
			this.get(i).deepFlush();
		}
		//(update time)
		DatasetTask dbTask = db.getObjectByKey(DatasetTask.class, "class", task.getClass()).perform();
		db.endTransaction();
		//--Run Downstream
		Iterator<Dependency> iter = db.getObjectsByKey(Dependency.class, "condition", dbTask.aid);
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
				db.deleteObjectsWhere(Dependency.class,"allows='"+toCreate.aid+"' AND condition='"+condition.aid+"'");
			}
		} else {
			//(get dependencies)
			Class<? extends Task>[] depends = task.dependencies();
			DatasetTask[] dependencies = new DatasetTask[depends.length];
			for(int i=0; i<depends.length; i++){
				dependencies[i] = db.getObjectByKey(DatasetTask.class, "class", depends[i]);
			}
			//(save object)
			DatasetTask dbTask = db.emptyObject(DatasetTask.class,task,dependencies).flush();
		}
		//--Run Task
		runTask(task);
		return this;
	}

	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("CoreMapDataset:\n");
		for(int i=0; i<this.numExamples(); i++){
			b.append("\t").append(this.get(i).toString()).append("\n");
		}
		return b.toString();
	}

	public static void main(String[] args){
		Database.ConnInfo psql = Database.ConnInfo.psql("localhost", "java", "what?why42?", "junit");
		Database db = new Database(psql).connect();
		db.clear();

		Annotation a = new Annotation("this is a sample sentence. This is another sentence");
		Annotation b = new Annotation("Some more sentences. Yes, this is another sentence as well.");
		CoreMapDataset data = new CoreMapDataset("trivial", db, new CoreMap[]{ a, b });

		System.out.println("----------------------\nRunning CORE annotator");
		data.runAndRegisterTask(new JavaNLPTasks.Core());
		System.out.println(data);
		System.out.println("----------------------\nRunning NER annotator");
		data.runAndRegisterTask( new JavaNLPTasks.NER() );
		System.out.println(data);
    System.out.println("----------------------\nRunning CORE annotator again");
		data.runAndRegisterTask(new JavaNLPTasks.Core());
		System.out.println(data);

		System.out.println("----------------------\nReloading Data");
		db.disconnect();
		db.connect();
		System.out.println( new CoreMapDataset("trivial", db, false) );
	}

}
