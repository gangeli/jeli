package org.goobs.stanford;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.Redwood;
import org.goobs.testing.Dataset;
import org.goobs.util.Range;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.Arrays;

public class SerializedCoreMapDataset extends Dataset<CoreMapDatum> implements Serializable, PrettyLoggable {
	private String file;

	private boolean isPiecewise = false;
	private CoreMapDatum[] maps;
	private WeakReference<CoreMapDatum>[] weakMaps;
	private File[] files;

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

	@SuppressWarnings({"unchecked"})
	public SerializedCoreMapDataset(String file){
		this.file = file;
		if(new File(file).isDirectory()){
			this.isPiecewise = true;
			//(get files)
			this.files = new File(file).listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.length() > 0;
				}
			});
			Arrays.sort(files);
			//(load files)
			this.weakMaps = (WeakReference<CoreMapDatum>[]) new WeakReference[files.length];
			for(int i=0; i<files.length; i++){
				this.weakMaps[i] = new WeakReference<CoreMapDatum>( null );
			}
		} else {
			this.isPiecewise = false;
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

	@Override public int numExamples() {
		if(isPiecewise){
			return weakMaps.length;
		} else {
			return maps.length;
		}
	}

	@Override public CoreMapDatum get(int id) {
		if(isPiecewise){
			WeakReference<CoreMapDatum> ref = weakMaps[id];
			CoreMapDatum rtn = ref.get();
			if(rtn == null){
				CoreMap impl = readObject(files[id].getPath());
				rtn = new CoreMapDatum(impl, id);
				weakMaps[id] = new WeakReference<CoreMapDatum>(rtn);
			}
			return rtn;
		} else {
			return maps[id];
		}
	}

	@Override
	public Range range() { return new Range(0,numExamples()); }

	@Override
	public void prettyLog(Redwood.RedwoodChannels redwoodChannels, String description) {
		Redwood.startTrack(description);
		for(int i=0; i<this.size(); i++){
			redwoodChannels.prettyLog("Datum " + i, this.get(i));
		}
		Redwood.endTrack(description);
	}

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
