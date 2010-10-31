package org.goobs.tests;

import static org.junit.Assert.*;
import org.junit.*;

import org.goobs.exec.Execution;
import org.goobs.exec.Log;
import org.goobs.exec.ThreadedFunction;
import org.goobs.exec.ThreadedTask;


public class ThreadedTaskTest{

	private boolean logOutput = false;
	
	@Test
	public void testBasic(){
		Execution.exec(new Runnable(){
			@Override
			public void run() {
				ThreadedTask t = new ThreadedTask();
				ThreadedTask t2 = new ThreadedTask();
				final int[] theInt = new int[2];
				int shouldBe = 0; int shouldBe2 = 0;
				ThreadedFunction <Integer,Integer> f = new ThreadedFunction<Integer,Integer>(){
					@Override
					public Integer call(Integer in) {
//						Utils.sleep(10);
						if(logOutput){ Log.log("Function (f1) called on input: " + in); }
						return in;
					}
					@Override
					public void process(Integer out) {
						theInt[0] += out;
					}
				};
				ThreadedFunction <Integer,Integer> f2 = new ThreadedFunction<Integer,Integer>(){
					@Override
					public Integer call(Integer in) {
						if(logOutput){ Log.log("Function (f2) called on input: " + in); }
						return in;
					}
					@Override
					public void process(Integer out) {
						theInt[1] += out;
					}
				};
				
				t.prepare();
				t2.prepare();
				for(int i=0; i<1000; i++){
					t.doFunction(f, i);
					shouldBe += f.call(i);
					t2.doFunction(f2, i);
					shouldBe2 += f2.call(i);
				}
				t.waitUntilCompleted();
				t2.waitUntilCompleted();
				
				ThreadedTask t3 = new ThreadedTask();
				t3.prepare();
				t3.waitUntilCompleted();
				
				assertEquals(shouldBe, theInt[0]);
				assertEquals(shouldBe2, theInt[1]);
			}
		});
	}
}
