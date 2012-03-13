package org.goobs.scheme;

import org.goobs.exec.Channel;
import org.goobs.util.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class SchemeThreadPool {
	protected static final int ROOT_THREAD = 0;
	
	private Scheduler manager;
	private Map <Integer,Channel<Boolean>> waitConditions = new HashMap <Integer,Channel<Boolean>>();
	private Lock waitConditionsLock = new ReentrantLock();
	
	protected  class SchemeThread extends Thread{
		protected int tid;
		protected int parentTid;
		private ScmObject toEval;
		private Environment env;
		private ScmObject toFill;
		
		public SchemeThread(ScmObject toEval, Environment env,
				ScmObject toFill, int tid, int parentTid) {
			this.tid = tid;
			this.parentTid = parentTid;
			this.toEval = toEval;
			this.env = env;
			this.toFill = toFill;
		}
		public void run(){
			try {
				ScmObject result = env.eval(toEval, this);
				toFill.setTo(result);
			} catch (SchemeException e) {
				manager.exception(e);
			}
			manager.release(this);
		}
		public void cont(){
			waitConditionsLock.lock();
			waitConditions.get(tid).send(true);	//signals endAppendAndWait()
			waitConditionsLock.unlock();
		}
	}

	private class Scheduler extends Thread{
		
		private Semaphore availableThreads;
		private Channel<SchemeThread> resumeQueue = new Channel<SchemeThread>();
		private Channel<SchemeThread> waitingToStart = new Channel <SchemeThread>();
		private Map <Integer, Pair<SchemeThread,Integer>> waitingOnChildren 
			= new HashMap <Integer, Pair<SchemeThread,Integer>> ();
		private Lock waitOnChildrenLock = new ReentrantLock();
		protected SchemeException except;
		
		private Scheduler(int numThreads){
			availableThreads = new Semaphore(numThreads);
		}
		
		public void run(){
			//--Resume watcher
			new Thread(){
				public void run(){
					while(true){
						SchemeThread thread = resumeQueue.recieve();
						if(except == null){
							availableThreads.acquireUninterruptibly();
							thread.cont();
						}else{
							release(thread);
						}
					}
				}
			}.start();
			//--New thread watcher
			new Thread(){
				public void run(){
					while(true){
						SchemeThread thread = waitingToStart.recieve();
						if(!resumeQueue.isEmpty()){Thread.yield();}
						if(except == null){
							availableThreads.acquireUninterruptibly();
							thread.start();
						}else{
							release(thread);
						}
					}
				}
			}.start();
		}
		
		/**
		 * Signal that a thread is ready to be executed
		 * 
		 * @param thread The Scheme thread which is waiting to run
		 */
		public void enqueue(SchemeThread thread){
			waitingToStart.send(thread);
		}
		
		/**
		 * Signal that a thread is done executing
		 * @param thread The Scheme thread which has finished
		 */
		public void release(SchemeThread thread){
			waitOnChildrenLock.lock();
			//(special case for root thread finishing)
			if(thread.tid == ROOT_THREAD){
				waitOnChildrenLock.unlock();
				availableThreads.release();
				done.release();
				return;
			}
			//(get the parent thread)
			Pair <SchemeThread, Integer> info = waitingOnChildren.get( thread.parentTid );
			//(some error checks)
			if(info == null){
				System.err.println("Illegal State in SchemeThreadPool.Manager.done(): null info. thread.tid=" + thread.tid);
				System.exit(1);
			}
			if(info.cdr() <= 0){
				System.err.println("Illegal State in SchemeThreadPool.Manager.done(): parent not waiting on children");
				System.exit(1);
			}
			//(signal to parent that we're done)
			info.setCdr(info.cdr() - 1);
			//(check if all children are done)
			if(info.cdr() == 0){
				resumeQueue.send(info.car());
				waitingOnChildren.remove(thread.parentTid);
			}
			waitOnChildrenLock.unlock();
			//(free thread)
			availableThreads.release();
		}
		
		/**
		 * Signal that a thread is going to wait on children
		 * 
		 * @param thread The Scheme thread which is about to wait
		 */
		public void yield(SchemeThread thread, int numChildren){
			waitOnChildrenLock.lock();
			waitingOnChildren.put(thread.tid, new Pair<SchemeThread,Integer>(thread, numChildren));
			waitOnChildrenLock.unlock();
			availableThreads.release();
		}
		
		public void exception(SchemeException e){
			except = e;
		}
	}
	
	
	protected SchemeThreadPool(int numThreads){
		this.manager = new Scheduler(numThreads);
		manager.start();
	}
	
	private Semaphore done = new Semaphore(1);
	
	protected void runAsRoot(ScmObject exp, Environment env, ScmObject rtn){
		//(wait our turn)
		done.acquireUninterruptibly();	//take a number
		//(execute stuff)
		SchemeThreadPool.SchemeThread root 
			= new SchemeThread(exp, env, rtn, SchemeThreadPool.ROOT_THREAD, -1);
		manager.enqueue(root);
		//(release our turn)
		done.acquireUninterruptibly();	//wait until someone yields
		done.release();					//we don't actually want it
		if(manager.except != null){
			SchemeException toThrow = manager.except;
			manager.except = null;
			throw toThrow;
		}
	}
	
	

	
	//append variables
	private Lock appendLock = new ReentrantLock();
	private Set <SchemeThread> toAppend;
	
	/**
	 * Obtain any relevant locks and get ready
	 * to recieve a series of append() calls
	 */
	protected void beginAppend(SchemeThread thisThread){
		//--Acquire append lock
		appendLock.lock();
		toAppend = new HashSet<SchemeThread>();
	}
	
	/**
	 * 
	 * Append a task to the run-queue. beginAppend()
	 * has already been called. The method is non-blocking
	 * 
	 * @param toEval The ScmObject to evaluate
	 * @param env The environment to execute the arguments in
	 * @param toFill The ScmObject to store the result of the evaluation in
	 */
	protected void append(ScmObject toEval, Environment env, ScmObject toFill, SchemeThread thisThread){
		if(toAppend == null){
			throw new IllegalStateException("Calling append before beginAppend");
		}
		if(thisThread == null){
			throw new IllegalStateException("NullPrior thread passed to append");
		}
		toAppend.add(new SchemeThread(toEval, env, toFill, nextThreadID(), thisThread.tid));
	}
	
	/**
	 * Releases any relevant locks, and recognizes that
	 * this append sequence is done. Will wait until all
	 * tasks since the last beginAppend() are complete.
	 */
	protected void endAppendAndWait(SchemeThread thisThread){
		//--Set up return framework
		//(finish condition)
		Channel <Boolean> isDone = new Channel<Boolean>();
		waitConditionsLock.lock();
		waitConditions.put(thisThread.tid, isDone);
		waitConditionsLock.unlock();
		//(let the manager know we're yielding)
		manager.yield(thisThread, toAppend.size());
		//--Start 'em off
		for(SchemeThread thread : toAppend){
			manager.enqueue(thread);
		}
		toAppend = null;
		//--Release append lock
		appendLock.unlock();
		//--Wait for children to finish
		isDone.recieve();	//signaled in SchemeThread.cont()
	}
	
	
	
	
	
	private static int nextID = ROOT_THREAD;
	protected static int nextThreadID(){
		nextID += 1;
		return nextID;
	}
	private static SchemeThreadPool instance = null;
	protected static final SchemeThreadPool makeInstance(int numThreads){
		if(instance != null){
			throw new IllegalStateException("Cannot make two instances of SchemeThreadPool");
		}
		instance = new SchemeThreadPool(numThreads);
		return instance;
	}
	protected static final SchemeThreadPool getInstance(){
		if(instance == null) throw new IllegalStateException("No SchemeThreadPool instance");
		return instance;
	}
	
}
