package org.goobs.tests;

import org.goobs.utils.Range;
import org.junit.*;
import static org.junit.Assert.*;


public class RangeTest {

	@Test
	public void testInRange(){
		Range r = new Range(1,2);
		assertTrue(r.inRange(1));
		assertFalse(r.inRange(2));
		assertFalse(r.inRange(0));
		assertFalse(r.inRange(-1));
		r = new Range(1,1);
		assertFalse(r.inRange(1));
	}
	
	@Test
	public void testGetters(){
		Range r = new Range(1,5);
		assertEquals(1, r.minInclusive());
		assertEquals(0, r.minExclusive());
		assertEquals(5, r.maxExclusive());
		assertEquals(4, r.maxInclusive());
		assertEquals(1, r.min());
		assertEquals(5, r.max());
	}
	
	@Test
	public void testDecodable(){
		Range r = (Range) (new Range(0,0)).decode("[1,5)",null);
		assertEquals(1, r.minInclusive());
		assertEquals(5, r.maxExclusive());
		r = (Range) (new Range(0,0)).decode("[1,5]",null);
		assertEquals(1, r.minInclusive());
		assertEquals(5, r.maxInclusive());
		r = (Range) (new Range(0,0)).decode("(1,5]",null);
		assertEquals(1, r.minExclusive());
		assertEquals(5, r.maxInclusive());
		r = (Range) (new Range(0,0)).decode("(1,5)",null);
		assertEquals(1, r.minExclusive());
		assertEquals(5, r.maxExclusive());
		
		r = (Range) (new Range(0,0)).decode("[1-5)",null);
		assertEquals(1, r.minInclusive());
		assertEquals(5, r.maxExclusive());
		r = (Range) (new Range(0,0)).decode("[1-5]",null);
		assertEquals(1, r.minInclusive());
		assertEquals(5, r.maxInclusive());
		r = (Range) (new Range(0,0)).decode("(1-5]",null);
		assertEquals(1, r.minExclusive());
		assertEquals(5, r.maxInclusive());
		r = (Range) (new Range(0,0)).decode("(1-5)",null);
		assertEquals(1, r.minExclusive());
		assertEquals(5, r.maxExclusive());
		
		r = (Range) (new Range(0,0)).decode("1-5",null);
		assertEquals(1, r.minInclusive());
		assertEquals(5, r.maxExclusive());
		
		try{
			r = (Range) (new Range(0,0)).decode("1~5",null);
			assertTrue(false);
		}catch(IllegalArgumentException e){}
		try{
			r = (Range) (new Range(0,0)).decode("1 5",null);
			assertTrue(false);
		}catch(IllegalArgumentException e){}
		try{
			r = (Range) (new Range(0,0)).decode("1.3-5",null);
			assertTrue(false);
		}catch(IllegalArgumentException e){}
		try{
			r = (Range) (new Range(0,0)).decode("1",null);
			assertTrue(false);
		}catch(IllegalArgumentException e){}
		try{
			r = (Range) (new Range(0,0)).decode("(1-)",null);
			assertTrue(false);
		}catch(IllegalArgumentException e){}
	}
}
