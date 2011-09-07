package org.goobs.stanford;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.CoreMap;
import org.goobs.testing.Dataset;
import org.goobs.utils.Range;

import java.io.*;
import java.util.Arrays;

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
		if(new File(file).isDirectory()){
			File[] maps = new File(file).listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.length() > 0;
				}
			});
			Arrays.sort(maps);
			this.maps = new CoreMapDatum[maps.length];
			for(int i=0; i<maps.length; i++){
				System.out.println("loading " + maps[i].getPath());
				this.maps[i] = new CoreMapDatum( (CoreMap) readObject(maps[i].getPath()), i);
			}
		} else {
			SerializedCoreMapDataset term = readObject(this.file);
			this.file = term.file;
			this.maps = term.maps;
		}
	}


	private void save(){
		if(new File(file).isDirectory()){
			throw new IllegalStateException("Cannot save to a dataset created from a directory");
		} else {
			writeObject(this.file, this);
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


	@SuppressWarnings({"unchecked"})
	private static <T> T readObject(String file){
		try{
			return (T) IOUtils.readObjectFromFile(file);
//			FileInputStream fos = new FileInputStream(file);
//			ObjectInputStream out = new ObjectInputStream(fos);
//			T term = (T) out.readObject();
//			out.close();
//			return term;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static <T> void writeObject(String file, T object){
		try{
			IOUtils.writeObjectToFile(object, file);
//			FileOutputStream fos = new FileOutputStream(file);
//			ObjectOutputStream out = new ObjectOutputStream(fos);
//			out.writeObject(object);
//			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
