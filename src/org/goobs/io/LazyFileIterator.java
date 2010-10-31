/**
 * 
 */
package org.goobs.io;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.Stack;

public class LazyFileIterator implements Iterator<File>{

	private FilenameFilter filter;
	private File[] dir;
	private Stack<File[]> parents = new Stack<File[]>();
	private Stack<Integer> indices = new Stack<Integer>();
	
	private int toReturn = -1;
	
	
	public LazyFileIterator(String path){
		this(new File(path));
	}
	public LazyFileIterator(File path){
		this(path, new FilenameFilter(){
			@Override
			public boolean accept(File dir, String name) {
				return true;
			}
		});
	}
	
	public LazyFileIterator(String path, FilenameFilter filter){
		this(new File(path), filter);
	}
	public LazyFileIterator(String path, final String filter){
		this(new File(path), filter);
	}
	public LazyFileIterator(File path, final String filter){
		this(path, new FilenameFilter(){
			@Override
			public boolean accept(File dir, String name) {
				String path = (dir.getPath() + "/" + name);
				return new File(path).isDirectory() || path.matches(filter);
			}
		});
	}
	public LazyFileIterator(File dir, FilenameFilter filter){
		if(!dir.exists()) throw new IllegalArgumentException("Could not find directory: " + dir.getPath());
		if(!dir.isDirectory()) throw new IllegalArgumentException("Not a directory: " + dir.getPath() );
		this.filter = filter;
		this.dir = dir.listFiles(filter);
		enqueue();
	}
	
	private void enqueue(){
		toReturn += 1;
		boolean good = (toReturn < dir.length && !dir[toReturn].isDirectory());
		while(!good){
			if(toReturn >= dir.length){
				//(case: pop)
				if(parents.isEmpty()){
					toReturn = -1;
					return;	//this is where we exit
				}else{
					dir = parents.pop();
					toReturn = indices.pop();
				}
			} else if(dir[toReturn].isDirectory()){
				//(case: push)
				parents.push(dir);
				indices.push(toReturn + 1);
				dir = dir[toReturn].listFiles(filter);
				toReturn = 0;
			}else{
				throw new IllegalStateException("File is invalid, but in range and not a directory: " + dir[toReturn]);
			}
			//(check if good)
			good = (toReturn < dir.length && !dir[toReturn].isDirectory());
		}
		// if we reach here we found something
	}
	
	@Override
	public boolean hasNext() {
		return toReturn >= 0;
	}

	@Override
	public File next() {
		if(toReturn >= dir.length || toReturn < 0) throw new IllegalStateException("No more elements!");
		File rtn = dir[toReturn];
		enqueue();
		return rtn;
	}

	@Override
	public void remove() {
		throw new IllegalArgumentException("NOT IMPLEMENTED");
	}
	
}