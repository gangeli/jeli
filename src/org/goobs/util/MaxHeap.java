package org.goobs.util;

public class MaxHeap<E> extends Heap <E>{
	
	public MaxHeap(){
		super();
	}
	public MaxHeap(int initialCapacity){
		super(initialCapacity);
	}
	public MaxHeap(int initialCapacity, long maxCapacity){
		super(initialCapacity, maxCapacity);
	}

	public E popMax(){
		return pop();
	}
	
	@Override
	protected void heapifyDown(int pos) {
		while(true) {
			//(get largest element)
			int left = leftChild(pos);
			int right = rightChild(pos);
			int largest = pos;
			if(left < size() && score(left) > score(largest)){
				largest = left;
			}
			if(right < size() && score(right) > score(largest)){
				largest = right;
			}
			//(heapify)
			if(largest != pos){
				swap(pos, largest);
				pos = largest;	//recursive case
			}else{
				return;	//base case
			}
		}
	}

	@Override
	protected void heapifyUp(int pos) {
		while(pos != 0){
			int parent = parent(pos);
			if(score(parent) >= score(pos)){
				return;	//done heapifying
			}else{
				swap(pos, parent);	//swap up
				pos = parent; //heapify up further
			}
		}
	}

}
