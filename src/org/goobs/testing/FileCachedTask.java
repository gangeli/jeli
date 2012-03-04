package org.goobs.testing;

import java.io.File;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public abstract class FileCachedTask <E extends Datum> implements Task<E> {
	public final File marker;
	public final FileCachedTask<E>[] dependencies;

	public FileCachedTask(File marker, FileCachedTask<E>... dependencies){
		this.marker = marker;
		this.dependencies = dependencies;
	}

	@Override
	public String name() {
		throw new RuntimeException("NOT IMPLEMENTED");
	}

	@Override
	public boolean isSatisfied() {
		if(!marker.exists()){
			//(case: never run)
			return false;
		} else {
			long myTime = marker.lastModified();
			if(dependencies != null){
				for(FileCachedTask<E> dep : dependencies){
					if(!dep.marker.exists()){
						//(case: marker doesn't exist)
						return false;
					} else {
						if(myTime < dep.marker.lastModified()){
							//(case: marker more recent)
							return false;
						}
					}
				}
				//(case: all dependencies exist)
				return true;
			} else {
				//(case: no dependencies)
				return true;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public  Class[] dependencies(){
		if(dependencies == null){
			return new Class[0];
		} else {
			Class[] rtn = new Class[dependencies.length];
			for(int i=0; i<dependencies.length; i++){
				rtn[i] = dependencies[i].getClass();
			}
			return rtn;
		}
	}
}
