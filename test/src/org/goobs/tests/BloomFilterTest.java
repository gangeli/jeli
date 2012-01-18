package org.goobs.tests;

import static org.junit.Assert.*;

import org.goobs.util.Function;
import org.junit.*;

import java.util.UUID;

import org.goobs.util.BloomFilter;

public class BloomFilterTest{

	@Test
	public void testBasic(){
		BloomFilter<String> test = new BloomFilter<String>(1000);
		assertTrue(test.isEmpty());
		assertEquals(0, test.size());
		test.addHashFunction(new Function<String,Integer>(){
			private static final long serialVersionUID = -1596497248768918102L;
			@Override
			public Integer eval(String input) {
				return input.hashCode();
			}
		});
		
		test.add("hello there");
		assertTrue(test.contains("hello there"));
		assertFalse(test.contains("hello theree"));
		assertFalse(test.contains("hello world"));
		test.add("hello world");
		assertTrue(test.contains("hello world"));
		assertFalse(test.contains("hello theree"));
		assertTrue(test.contains("hello there"));
		assertEquals(2, test.size());
	}
	
	@Test
	public void testFuzz(){
		int num = 100000;
		
		BloomFilter<String> test = new BloomFilter<String>(100*num);
		test.addHashFunction(new Function<String,Integer>(){
			private static final long serialVersionUID = -1596497248768918102L;
			@Override
			public Integer eval(String input) {
				return input.hashCode();
			}
		});
		
		for(int i=0; i<num; i++){
			String str = UUID.randomUUID().toString();
			test.add(str);
			assertTrue(test.contains(str));
		}
		assertEquals(num, test.size());
		
		double falsePos = 0;
		for(int i=0; i<num; i++){
			String str = UUID.randomUUID().toString();
			if(test.contains(str)){
				falsePos += 1;
			}
		}
		assertTrue(falsePos / ((double) num) < 0.015);	//ave rate is 0.01
	}
    

}