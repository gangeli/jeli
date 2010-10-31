package org.goobs.tests;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.goobs.io.ReliableChannel;


public class ReliableChannelTest{

	private static class NoisyConnection{
		private double successProb;
		private boolean corruptPackets;
		
		private Queue<byte[]> aToB = new LinkedList<byte[]>();
		private int bAvailable = 0;
		private byte[] bNextSeq = null;
		private int bNextIndex = -1;
		private Queue<byte[]> bToA = new LinkedList<byte[]>();
		private int aAvailable = 0;
		private int aNextIndex = -1;
		private byte[] aNextSeq = null;
		
		private Lock inALock = new ReentrantLock();
		private Condition inACond = inALock.newCondition();
		private Lock inBLock = new ReentrantLock();
		private Condition inBCond = inBLock.newCondition();
		
		public final InputStream inA = new InputStream(){
			@Override
			public int available(){
				inALock.lock();
				int rtn = aAvailable;
				inALock.unlock();
				return rtn;
			}
			@Override
			public int read() throws IOException {
				while(aNextSeq == null || aNextIndex >= aNextSeq.length){
					inALock.lock();
					aNextSeq = bToA.poll();
					aNextIndex = 0;
					inALock.unlock();
				}
				int rtn = (int) aNextSeq[aNextIndex];
				aNextIndex += 1;
				inALock.lock(); aAvailable -= 1; inALock.unlock();
				return rtn;
			}
		};
		public final InputStream inB = new InputStream(){
			@Override
			public int available(){
				inBLock.lock();
				int rtn = bAvailable;
				inBLock.unlock();
				return rtn;
			}
			@Override
			public int read() throws IOException {
				while(bNextSeq == null || bNextIndex >= bNextSeq.length){
					inBLock.lock();
					bNextSeq = aToB.poll();
					bNextIndex = 0;
					inBLock.unlock();
				}
				int rtn = (int) bNextSeq[bNextIndex];
				bNextIndex += 1;
				inBLock.lock(); bAvailable -= 1; inBLock.unlock();
				return rtn;
			}
		};
		public final OutputStream outA = new OutputStream(){
			@Override
			public void write(int arg0) throws IOException {
				sendFromA(new byte[]{(byte) arg0});
			}
			@Override
			public void write(byte[] bytes) throws IOException {
				if(!corruptPackets){
					sendFromA(bytes);
				}else{
					for(int i=0; i<bytes.length; i++){
						write(bytes[i]);
					}
				}
			}
		};
		public final OutputStream outB = new OutputStream(){
			@Override
			public void write(int arg0) throws IOException {
				sendFromB(new byte[]{(byte) arg0});
			}
			@Override
			public void write(byte[] bytes) throws IOException {
				if(!corruptPackets){
					sendFromB (bytes);
				}else{
					for(int i=0; i<bytes.length; i++){
						write(bytes[i]);
					}
				}
			}
		};
		private NoisyConnection(double successProb){
			this(successProb, false);
		}
		private NoisyConnection(double successProb, boolean corruptPackets){
			this.successProb = successProb;
			this.corruptPackets = corruptPackets;
		}
		private void sendFromA(byte[] b){
			if((new Random()).nextDouble() <= successProb){
//				Log.log("Send: a->b: " + Arrays.toString(b));
				inBLock.lock();
				aToB.offer(b);
				bAvailable += b.length;
				inBCond.signalAll();
				inBLock.unlock();
			}else{
//				Log.log("FAILED: a->b: " + Arrays.toString(b));
			}
		}
		private void sendFromB(byte[] b){
			if((new Random()).nextDouble() <= successProb){
//				Log.log("Send: b->a: " + Arrays.toString(b));
				inALock.lock();
				bToA.offer(b);
				aAvailable += b.length;
				inACond.signalAll();
				inALock.unlock();
			}else{
//				Log.log("FAILED: b->a: " + Arrays.toString(b));
			}
		}
	}


	private void deterministicSequence(ReliableChannel a, ReliableChannel b, int num){
		a.setName("A");
		a.start();
		b.setName("B");
		b.start();

		//--Connect Test
		assertTrue( a.connect() );
		assertTrue( a.isValid() );
		assertTrue( b.isValid() );
		
		//--Send Stuff
		for(int i=0; i<num; i++){
			//(send a->b)
			String msg = UUID.randomUUID().toString();
			assertTrue( a.send(msg.getBytes()) );
			assertTrue( a.isValid() );
			assertTrue( b.isValid() );
			assertEquals(msg, new String(b.receive()));
			assertTrue( a.isValid() );
			assertTrue( b.isValid() );
			//(send a->b)
			msg = UUID.randomUUID().toString();
			assertTrue( a.send(msg.getBytes()) );
			assertTrue( a.isValid() );
			assertTrue( b.isValid() );
			assertEquals(msg, new String(b.receive()));
			assertTrue( a.isValid() );
			assertTrue( b.isValid() );
			//(send b->a)
			msg = UUID.randomUUID().toString();
			assertTrue( b.send(msg.getBytes()) );
			assertTrue( b.isValid() );
			assertTrue( a.isValid() );
			assertEquals(msg, new String(a.receive()));
			assertTrue( a.isValid() );
			assertTrue( b.isValid() );
		}
		
		//--Close Connection
		assertTrue( a.disconnect() );
		assertTrue( a.isValid() );
		assertTrue( b.isValid() );
	}

	@Test
	public void testNoPacketLoss(){
		NoisyConnection conn = new NoisyConnection(1.0);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,10000);
	}
	
	@Test
	public void test20PacketLoss(){
		NoisyConnection conn = new NoisyConnection(0.80);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,100);
	}
	
	@Test
	public void test50PacketLoss(){
		NoisyConnection conn = new NoisyConnection(0.50);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,100);
	}
	
	@Test
	public void test90PacketLoss(){
		NoisyConnection conn = new NoisyConnection(0.10);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,10);
	}
	
	@Test
	public void test5PacketLossAndCorrupted(){
		NoisyConnection conn = new NoisyConnection(0.95, true);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,100);
	}
	
	@Test
	public void test10PacketLossAndCorrupted(){
		NoisyConnection conn = new NoisyConnection(0.90, true);
		ReliableChannel a = new ReliableChannel(conn.outA, conn.inA, conn.inALock, conn.inACond);
		ReliableChannel b = new ReliableChannel(conn.outB, conn.inB, conn.inBLock, conn.inBCond);
		
		deterministicSequence(a,b,10);
	}
	
}
