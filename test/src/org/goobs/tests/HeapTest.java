package org.goobs.tests;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.Arrays;
import java.util.Random;

import org.goobs.utils.MaxHeap;
import org.goobs.utils.MinHeap;

public class HeapTest{

	@Test
	public void testSimpleMax(){
		MaxHeap<Integer> max = new MaxHeap<Integer>();
		max.push(1, 1.0);
		max.push(4, 4.0);
		max.push(2, 2.0);
		max.push(10, 10.0);
		max.push(8, 8.0);
		assertEquals(10, (int) max.popMax());
		assertEquals(8, (int) max.popMax());
		assertEquals(4, (int) max.popMax());
		assertEquals(2, (int) max.popMax());
		assertEquals(1, (int) max.popMax());
	}
	
	@Test
	public void testSimpleMin(){
		MinHeap<Integer> min = new MinHeap<Integer>();
		min.push(1, 1.0);
		min.push(4, 4.0);
		min.push(2, 2.0);
		min.push(10, 10.0);
		min.push(8, 8.0);
		assertEquals(1, (int) min.popMin());
		assertEquals(2, (int) min.popMin());
		assertEquals(4, (int) min.popMin());
		assertEquals(8, (int) min.popMin());
		assertEquals(10, (int) min.popMin());
	}
	
	@Test
	public void testFuzz(){
		for(int i=0; i<1000; i++){
			//(variables)
			int size = new Random().nextInt(10000);
			MaxHeap<Integer> max = new MaxHeap<Integer>();
			MinHeap<Integer> min = new MinHeap<Integer>();
			int[] check = new int[size];
			//(add terms)
			for(int j=0; j<size; j++){
				int element = new Random().nextInt(size*5);
				double score = (double) element;
				max.push(element, score);
				min.push(element, score);
				check[j] = element;
			}
			//(sort)
			Arrays.sort(check);
			//(check)
			for(int j=0; j<size; j++){
				int checkMax = check[size-j-1];
				int checkMin = check[j];
				int empMax = max.popMax();
				int empMin = min.popMin();
				assertEquals(checkMin, empMin);
				assertEquals(checkMax, empMax);
			}
		}
	}
	
}
