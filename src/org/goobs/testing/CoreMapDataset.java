package org.goobs.testing;


import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.database.*;
import org.goobs.utils.Range;

import java.util.*;

public class CoreMapDataset extends Dataset<DBCoreMap>{

	@Table(name="coremap_dataset")
	private static class Dataset extends DatabaseObject{
		@PrimaryKey(name="did")
		private int did;
		@Index
		@Key(name="name", length=127)
		private String name;
		@Key(name="maps")
		private String[] maps;
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




	public static void main(String[] args){
		Properties props = new Properties();
		props.setProperty("annotators","tokenize, ssplit, pos, lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		Annotation input = new Annotation("this is a sample sentence. This is another sentence");
		pipeline.annotate(input);

		Database.ConnInfo psql = Database.ConnInfo.psql("localhost", "java", "what?why42?", "junit");
		Database db = new Database(psql).connect();
		db.clear();

		CoreMapDataset data = new CoreMapDataset("trivial", db, new CoreMap[]{input} );

		db.disconnect();
		db.connect();

		data = new CoreMapDataset("trivial", db, false);

		for(int i=0; i<data.numExamples(); i++){
			System.out.println(data.get(i));
		}

	}

}
