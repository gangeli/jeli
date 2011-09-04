package org.goobs.stanford;

import edu.stanford.nlp.util.CoreMap;
import org.goobs.testing.Dataset;
import org.goobs.utils.Range;

import java.io.*;

public class SerializedCoreMapDataset extends Dataset<CoreMapDatum> implements Serializable{
	private String file;
	private CoreMapDatum[] maps;

	public SerializedCoreMapDataset(String file, CoreMap[] maps){
		//(create dataset)
		this.file = file;
		this.maps = new CoreMapDatum[maps.length];
		for(int i=0; i<maps.length; i++){
			this.maps[i] = new CoreMapDatum(maps[i],i);
		}
		//(save dataset)
		save();
	}

	public SerializedCoreMapDataset(String file){
		this.file = file;
		try {
			FileInputStream fos = new FileInputStream(this.file);
			ObjectInputStream out = new ObjectInputStream(fos);
			SerializedCoreMapDataset term = (SerializedCoreMapDataset) out.readObject();
			this.file = term.file;
			this.maps = term.maps;
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void save(){
		try {
			FileOutputStream fos = new FileOutputStream(this.file);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(this);
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public int numExamples() { return maps.length; }

	@Override public CoreMapDatum get(int id) { return maps[id]; }

	@Override
	public Range range() { return new Range(0,numExamples()); }

	@SuppressWarnings({"unchecked"})
	public <E extends Task> SerializedCoreMapDataset runAndRegisterTask(E task){
		this.file = this.file+"-"+task.name();
		task.perform(this);
		this.save();
		return this;
	}
}
