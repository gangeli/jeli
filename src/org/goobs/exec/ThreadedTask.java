package org.goobs.exec;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.goobs.exec.Log.*;

import org.goobs.utils.Pair;

public class ThreadedTask {

	@Option(gloss="Number of threads to run at any given time")
	private static int numThreads = 4;
	
	private static int numTasksRunning = 0; //number of ThreadedTasks running
	private static int numManagersRunning = 0; //number of manager threads running
	private static Lock taskLifecycleLock = new ReentrantLock(); //global lock (for above variables)
	private static Condition exitCond = taskLifecycleLock.newCondition(); //signals when a manager exits
	
	private static Semaphore threads;	//limit on the number of threads running
	private static Channel <Runnable> runQueue; //threads waiting to run
	private static Channel <Pair<Throwable,ThreadedFunction<?,?>>> exceptionQueue; //exceptions waiting to be handled
	
	
	private String name; //the task's name
	private boolean prepared = false; //whether we're prepared to accept functions
	private Lock processLock = new ReentrantLock(); //lock on the synchronized part of the function
	private int threadsPending = 0; //number of threads still not completed
	private Lock threadsPendingLock = new ReentrantLock(); //concurrency lock on threadsPending
	private Condition threadsPendingCond = threadsPendingLock.newCondition(); //signals when a thread finishes
	
	
	public ThreadedTask(){
		this(null);
	}
	
	public ThreadedTask(String name){
		//--Initialize Variables
		if(threads == null){
			if(numThreads <= 0){ throw fail("Must have > 0 threads: " + numThreads); }
			threads = new Semaphore(numThreads);
			runQueue = new Channel <Runnable> ();
			exceptionQueue = new Channel <Pair<Throwable,ThreadedFunction<?,?>>> ();
		}
		//--Initialize Task
		this.name = name;
	}
	
	public String getName(){
		return name;
	}
	
	public ThreadedTask prepare(){
		//--Start Managers (if applicable)
		taskLifecycleLock.lock();
		if(numTasksRunning == 0){
			//(wait for last run to finish)
			while(numManagersRunning > 0){
				exitCond.awaitUninterruptibly();
			}
			//(signal multithreading to log)
			Log.signalThreads();
			//--Start Managers
			numManagersRunning += 2;	//2 managers total
			//(manager)
			new Thread(){
				@Override
				public void run(){
					try {
						while(true){
							Runnable r = runQueue.recieve();
							//(shouldExit)
							taskLifecycleLock.lock();
							if(numTasksRunning == 0){
								numManagersRunning -= 1; //we'll exit shortly
								exitCond.signalAll();
								taskLifecycleLock.unlock();
								break;
							}
							taskLifecycleLock.unlock();
							//(handle runnable)
							threads.acquireUninterruptibly();
							new Thread(r).start();
						}
					} catch (Exception e) {
						fatal(e);	//we should never reach here
					}
				}
			}.start();
			//(exception handler)
			new Thread(){
				@Override
				public void run(){
					try {
						while(true){
							Pair <Throwable,ThreadedFunction<?,?>> pair = exceptionQueue.recieve();
							//(should exit)
							taskLifecycleLock.lock();
							if(numTasksRunning == 0){
								numManagersRunning -= 1; //we'll exit shortly
								exitCond.signalAll();
								taskLifecycleLock.unlock();
								break;
							}
							taskLifecycleLock.unlock();
							//(handle exception)
							if(pair != null){
								err("Exception in threaded function " + pair.cdr() + ":");
								pair.car().printStackTrace();
								exit(ExitCode.FATAL_EXCEPTION);
							}
						}
					} catch (Exception e) {
						fatal(e);	//we should never reach here
					}
				}
			}.start();
		}
		//(mark task as running)
		numTasksRunning += 1;
		taskLifecycleLock.unlock();
		
		//--Mark as Prepared
		prepared = true;
		return this;
	}
	
	public <I,O> void doFunction(final ThreadedFunction<I,O> func, final I in){
		if(!prepared){ throw fail("Not prepared; call prepare() before calling this function"); }
		threadsPendingLock.lock();
		threadsPending += 1;
		runQueue.send(new Runnable(){
			@Override
			public void run() {
				//(call the function)
				O out = func.call(in);
				//(process the result)
				processLock.lock();
				func.process(out);
				processLock.unlock();
				//(signal that we're done)
				threadsPendingLock.lock();
				threads.release(); //release the permit
				threadsPending -= 1;
				if(threadsPending == 0){
					threadsPendingCond.signalAll();
				}
				threadsPendingLock.unlock();
			}
		});
		threadsPendingLock.unlock();
	}
	
	public void waitUntilCompleted(){
		if(!prepared){ throw fail("Not prepared; call prepare() before calling this function"); }
		//(wait for threads)
		threadsPendingLock.lock();
		while(threadsPending > 0){
			threadsPendingCond.awaitUninterruptibly();
		}
		threadsPendingLock.unlock();
		//(shut down managers -- if applicable)
		taskLifecycleLock.lock();
		numTasksRunning -= 1;
		if(numTasksRunning == 0){
			shutdown();
		}
		taskLifecycleLock.unlock();
	}
	
	private void shutdown(){
		if(threads.availablePermits() != numThreads){ throw internal("No tasks running, but not all thread permits returned! permits: " + threads.availablePermits() + " numThreads: " + numThreads); }
		runQueue.send(null);
		exceptionQueue.send(null);
		Log.endThreads();
	}
	
//	public static void main(String[] args){
//		Execution.exec(new Runnable(){
//			@Override
//			public void run() {
//				ThreadedTask t = new ThreadedTask();
//				ThreadedTask t2 = new ThreadedTask();
//				final int[] theInt = new int[2];
//				int shouldBe = 0; int shouldBe2 = 0;
//				ThreadedFunction <Integer,Integer> f = new ThreadedFunction<Integer,Integer>(){
//					@Override
//					public Integer call(Integer in) {
//						return in;
//					}
//					@Override
//					public void process(Integer out) {
//						theInt[0] += out;
//					}
//				};
//				ThreadedFunction <Integer,Integer> f2 = new ThreadedFunction<Integer,Integer>(){
//					@Override
//					public Integer call(Integer in) {
//						return in;
//					}
//					@Override
//					public void process(Integer out) {
//						theInt[1] += out;
//					}
//				};
//				
//				t.prepare();
//				t2.prepare();
//				for(int i=0; i<1000; i++){
//					t.doFunction(f, i);
//					shouldBe += f.call(i);
//					t2.doFunction(f2, i);
//					shouldBe2 += f2.call(i);
//				}
//				t.waitUntilCompleted();
//				t2.waitUntilCompleted();
//				
//				ThreadedTask t3 = new ThreadedTask();
//				t3.prepare();
//				t3.waitUntilCompleted();
//			}
//		}, new String[0]);
//	}
}
