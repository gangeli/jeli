package org.goobs.tests;
import static org.junit.Assert.*;
import org.junit.*;

import org.goobs.utils.Circle;

public class CircleTest{

	@Test
	public void testConstructors(){
		new Circle();
		new Circle(0.0, 0.0, 0.0);
	}
	
	@Test
	public void testBasics(){
		assertEquals(new Circle(0,0,5).area(), Math.PI*25.0, 0.0);
	}
	
	@Test
	public void testRadians(){
		//--Simple distance
		double dist = new Circle().equidistantPoints(1).radiansFrom(0, 0);
		assertEquals(dist, Math.PI * 2.0, 0.0);
		dist = new Circle().equidistantPoints(2).radiansFrom(0, 1);
		assertEquals(dist, Math.PI, 0.0);
		dist = new Circle().equidistantPoints(4).radiansFrom(0, 1);
		assertEquals(dist, Math.PI / 2.0, 0.0);
		dist = new Circle().equidistantPoints(4).radiansFrom(0, 2);
		assertEquals(dist, Math.PI, 0.0);
		dist = new Circle().equidistantPoints(4).radiansFrom(2, 0);
		assertEquals(dist, Math.PI, 0.0);
		dist = new Circle().equidistantPoints(4).radiansFrom(3, 1);
		assertEquals(dist, Math.PI, 0.0);
		
		//--Object distance
		dist = new Circle().equidistantPoints(new Integer[]{1}).radiansFrom(1, 1);
		assertEquals(dist, Math.PI * 2.0, 0.0);
		dist = new Circle().equidistantPoints(new Integer[]{1, 2}).radiansFrom(1, 2);
		assertEquals(dist, Math.PI, 0.0);
		dist = new Circle().equidistantPoints(new Integer[]{1, 2, 3, 4}).radiansFrom(1, 2);
		assertEquals(dist, Math.PI / 2.0, 0.0);
		dist = new Circle().equidistantPoints(new Integer[]{1, 2, 3, 4}).radiansFrom(2, 0);
		assertEquals(dist, Math.PI, 0.0);
		dist = new Circle().equidistantPoints(new Integer[]{1, 2, 3, 4}).radiansFrom(3, 1);
		assertEquals(dist, Math.PI, 0.0);
	}
}
