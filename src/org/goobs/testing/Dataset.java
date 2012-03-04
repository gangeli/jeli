package org.goobs.testing;

import edu.stanford.nlp.util.MetaClass;
import org.goobs.util.Pair;
import org.goobs.util.Range;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class Dataset <D extends Datum> implements java.io.Serializable, Iterable<D>{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Number of examples in the dataset.
	 * @return Number of examples in the dataset
	 */
	public abstract int numExamples();
	/**
	 * Get a datum with id (at index) id. These ids should range
	 * from [0 , numExamples()).
	 * @param id The id of the object to be retrieved
	 * @return The object with id (at index) id.
	 */
	public abstract D get(int id);
	/**
	 * The range of indices this dataset encompases
	 * @return A range of valid IDs
	 */
	public abstract Range range();
	/**
	 * Creates a slice of the dataset between the indices [startInclusive,stopExclusive).
	 * @param startInclusive The (inclusive) start index
	 * @param stopExclusive The (exclusive) stop index
	 * @return A dataset which only covers this range
	 */	
	public Dataset <D> slice(int startInclusive, int stopExclusive){
		return new DatasetSlice<D>(this, startInclusive, stopExclusive, false);
	}
	/**
	 * Creates a slice of the dataset between on all datums
	 * except between indices [startInclusive,stopExclusive).
	 * @param startInclusive The (inclusive) start index
	 * @param stopExclusive The (exclusive) stop index
	 * @return A dataset which covers everything but this range
	 */
	public Dataset <D> butSlice(int startInclusive, int stopExclusive){
		return new DatasetSlice<D>(this, startInclusive, stopExclusive, true);
	}
	
	/**
	 * The size of the dataset (same as numExamples())
	 * @return Number of examples in the dataset
	 */
	public int size(){ return numExamples(); }

	/**
	 * Create an iterator over the dataset. 
	 * @return An iterator over the dataset
	 */
	public Iterator<D> iterator(){
		final Range r = this.range();
		return new Iterator<D>(){
			private int index = r.minInclusive();
			@Override
			public boolean hasNext() {
				return index < r.maxExclusive();
			}
			@Override
			public D next() {
				if(index >= r.maxExclusive()){ throw new NoSuchElementException(); }
				D rtn = get(index);
				index += 1;
				return rtn;
			}
			@Override
			public void remove() {
				throw new IllegalArgumentException("Cannot remove from dataset iterator");
			}
			
		};
	}
	
	/**
	 * Creates a 'fold' of the dataset, splitting it into training and test.
	 * For data which does not split evenly into n folds, the behavior is to
	 * spread the remainder over the first folds.
	 * @param foldIndex The index of the fold, ranging from [0,numFolds)
	 * @param numFolds The number of folds to take
	 * @return A Pair of the (training,test) datasets
	 */
	public Pair<Dataset<D>,Dataset<D>> fold(int foldIndex, int numFolds){
		int examplesPerFold = this.numExamples() / numFolds;
		int remainder = this.numExamples() % numFolds;
		
		int startInclusive = foldIndex*examplesPerFold + Math.min(remainder, foldIndex);
		int stopExclusive = startInclusive + examplesPerFold;
		if(foldIndex < remainder){ stopExclusive += 1; }
		
		Dataset<D> test = slice(startInclusive,stopExclusive);
		Dataset<D> train = butSlice(startInclusive,stopExclusive);
		return Pair.make(train,test);
	}
	
	/**
	 * Create a train/test split in which only one example is held out
	 * @param testedIndex The example to be held out
	 * @return A Pair of the (training data, held out datum)
	 */
	public Pair<Dataset<D>,D> butOne(int testedIndex){
		return Pair.make(
			butSlice(testedIndex,testedIndex+1),
			get(testedIndex)
			);
	}
	
	public static <E extends Datum> Dataset<E> ensure(Task<E> task){
		Dataset<E> rtn = null;
		if(!task.isSatisfied()){
			//--Ensure Dependencies
			for(Class dep : task.dependencies()){
				Task<E> depTask = MetaClass.create(dep).createInstance();
				ensure(depTask);
			}
			//--Run Task
			rtn = task.perform();
		} else {
			//--Load Task
			task.load();
		}
		//--Return
		if(!task.isSatisfied()){
			throw new IllegalStateException("Running task did not satisfy it!");
		}
		return rtn;
	}
	
	public static void main(String[] args){
		if(args.length != 1){
			System.err.println("Usage: Dataset [class_of_task]");
			System.exit(1);
		}
		Task<?> task = MetaClass.create(args[0]).createInstance();
		ensure(task);
	}
}
