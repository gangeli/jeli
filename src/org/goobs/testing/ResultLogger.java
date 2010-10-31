package org.goobs.testing;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;

public abstract class ResultLogger {
	public abstract String getIndexPath();
	public abstract String getPath();
	public abstract void setGlobalResult(String name, double value);
	public abstract void addGlobalString(String name, Object value);
	public abstract void setLocalResult(int index, String name, double value);
	public abstract void addLocalString(int index, String name, Object value);
	public abstract void add(int index, Object guess, Object gold);
	public abstract void save(String root, boolean index);
	
	public abstract ResultLogger spawnGroup(String name, int index);
	public abstract void suggestFlush();
	
	public void save(){
		save("ROOT", true);
	}
	
	protected DecimalFormat df = new DecimalFormat("0.0000");
	
	protected void appendToIndex(HashMap <String, Double> values){
		try{			
			//--Sort fields
			String[] keys = new String[values.keySet().size()];
			int index=0;
			for(String key : values.keySet()){
				keys[index] = key;
				index+=1;
			}
			Arrays.sort(keys);
			
			//--Create file structure
			String path = getPath();
			String indexPath = getIndexPath();
			File f = new File(indexPath);
			if(!f.exists()){
				if(!f.createNewFile()){
					throw new IllegalStateException("Could not create test log index file!");
				}
				FileWriter writer = new FileWriter(f);	//append to file
				writer.append("#");
				for(int i=0; i<keys.length; i++){
					writer.append(keys[i]).append("\t");
				}
				writer.append("path\n");
				writer.flush();
				writer.close();
			}
			
			//--Write line
			FileWriter writer = new FileWriter(f, true);	//append to file
			for(int i=0; i<keys.length; i++){
				writer.append( df.format( values.get(keys[i]) ) ).append("\t");
			}
			writer.append(path);
			writer.append("\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalStateException("IO Exception");
		}

	}
}
