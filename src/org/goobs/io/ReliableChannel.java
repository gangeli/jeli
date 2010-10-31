package org.goobs.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.goobs.exec.Log;
import org.goobs.exec.Option;
import org.goobs.utils.Pair;
import org.goobs.utils.Utils;

/*
 * Guarantees that all packets are
 * received on both ends, but does
 * not guarantee order
 * TODO corrupted packets are handled a bit buggily
 */
public class ReliableChannel extends Thread{

	@Option(name="reliableChannelTimeout")
	private static long TIMEOUT = 1000;
	@Option(name="reliableChannelConnectTimeout")
	private static final long CONN_TIMEOUT = 10000;
	
	
	private static class CorruptionError extends RuntimeException{
		private static final long serialVersionUID = -3042524073324882013L;
	}
	
	private static enum State{
		NOT_RUNNING,
		CLOSED,
		SYN_SENT,
		SYN_RCVD,
		ESTABLISHED,
		STP_SENT,
		STP_RCVD,
		CLOSING,
		BROKEN,
	}

	private static enum Type{
		SYN(1),
		SYNACK(2),
		DAT(3),
		ACK(4),
		STP(5),
		FIN(6),
		FINACK(7),
		;
		private byte code;
		Type(int code){
			this.code = (byte) code;
		}
	}

	@Option(name="channelLittleEndian")
	private static boolean LITTLE_ENDIAN = true;

	private static final int PacketLen = 11;
	private static final class Packet{
		private Type type;
		private int length;
		private int index;
		private byte checksum;
		private byte headerChecksum;
		private byte[] data;
		@Override
		public String toString(){
			return "";
		}
	}

	private OutputStream send;
	private InputStream receive;
	private Lock inputStreamLock = null;
	private Condition inputStreamDataReady = null;

	private boolean shouldRun = true;
	private State state = State.NOT_RUNNING;
	
	private AtomicInteger nextIndex = new AtomicInteger(0);
	private int lastRcvd = -1;
	private int lastSent = -1;

	private Lock queueLock = new ReentrantLock();
	private Condition packetReady = queueLock.newCondition();
	private Queue<byte[]> sendQueue = new LinkedList<byte[]>();
	private Queue<byte[]> receiveQueue = new LinkedList<byte[]>();

	private Lock connLock = new ReentrantLock();
	private Condition connectStatusChanged = connLock.newCondition();
	private Condition running = connLock.newCondition();
	
	private Lock userActionsLock = new ReentrantLock();
	private Queue <Runnable> userActions = new LinkedList <Runnable>();
	

	public ReliableChannel(OutputStream noisySend, InputStream noisyReceive){
		this(noisySend, noisyReceive, null, null);
	}

	public ReliableChannel(OutputStream noisySend, InputStream noisyReceive, Lock condLock, Condition inputReady){
		this.send = noisySend;
		this.receive = noisyReceive;
		setInputCondition(condLock, inputReady);
	}

	public void setInputCondition(Lock condLock, Condition inputReady){
		this.inputStreamLock = condLock;
		this.inputStreamDataReady = inputReady;
	}

	private boolean ensureReadable(int num) throws IOException{
		int ticks = 0;
		while(receive.available() < num){ 
			if(ticks > 100){
				return false;
			}else{
				Utils.sleep(10);
				ticks += 1;
			}
		}
		return true;
	}
	private int readInt(byte a, byte b, byte c, byte d){
		if(LITTLE_ENDIAN){
			return 0x0 | a << 24 | b << 16 | c << 8 | d;
		} else {
			return 0x0 | a | b << 8 | c << 16 | d << 24;
		}
	}
	private int readInt(Queue<Byte> overBuffer) throws IOException{
		int rtn = 0x0;
		for(int i=0; i<4; i++){
			byte b = (byte) ((overBuffer != null && !overBuffer.isEmpty()) ? overBuffer.poll() : receive.read());
			if(LITTLE_ENDIAN){
				rtn = rtn | b << 24-8*i;
			} else {
				rtn = rtn | b << 8*i;
			}
		}
		return rtn;
	}
	private void writeInt(int toSend, byte[] buffer, int start) throws IOException{
		for(int i=0; i<4; i++){
		    if(LITTLE_ENDIAN){
		    	buffer[start+i] = (byte) ((toSend & (0xFF << 24-8*i)) >> 24-8*i);
		    } else {
		    	buffer[start+i] = (byte) ((toSend & (0xFF << 8*i)) >> 8*i);
		    }
		  }
	}
	
	private void ensureData(long timeout) throws IOException{
		if(inputStreamLock != null){ inputStreamLock.lock(); }
		if(receive.available() == 0){
			//(wait for input)
			if(inputStreamDataReady != null){
				try {
					if(timeout >= 0){
						inputStreamDataReady.awaitNanos(timeout);
					}else{
						inputStreamDataReady.awaitUninterruptibly();
					}
				} catch (InterruptedException e) {
				}
			}else{
				int i=0;
				while(i<TIMEOUT/10 && receive.available() < PacketLen){ i += 1; Utils.sleep(10); }
			}
		}
		if(inputStreamLock != null){ inputStreamLock.unlock(); }
	}
	
	private Iterator <Byte> byteIter = new Iterator<Byte>(){
		@Override
		public boolean hasNext() {
			try {
				ensureData(-1);
			} catch (IOException e) {
				throw Log.fail(e);
			}
			return true;
		}
		@Override
		public Byte next() {
			try {
				ensureData(-1);
				return (byte) receive.read();
			} catch (IOException e) {
				throw Log.fail(e);
			}
		}
		@Override
		public void remove() {
			throw new NoSuchMethodError();
		}
		
	};
	
	private Pair<Packet,Queue<Byte>> readCorruptedPacket(){
		Queue <Byte> saveQueue = new LinkedList<Byte>();
		while(true){
			byte b = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			//--Match Type
			Type type = null;
			for(Type t : Type.values()){
				if(t.code == b){ type = t; }
			}
			if(type == null){ continue; /* keep trying */}
			//--Match Header
			//(read header)
			byte h1 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h2 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h3 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h4 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h5 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h6 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h7 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h8 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h9 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			byte h10 = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
			int length = readInt(h1, h2, h3, h4);
			int index = readInt(h5, h6, h7, h8);
			byte checksum = h9; 
			byte headerChecksum = h10;
			byte calcChecksum = (byte) (checksum ^ type.code ^ 
					((index>>0)|0xFF) ^ 
					((index>>8)|0xFF) ^
					((index>>16)|0xFF) ^
					((index>>24)|0xFF) ^
					((length>>0)|0xFF) ^
					((length>>8)|0xFF) ^
					((length>>16)|0xFF) ^
					((length>>24)|0xFF) ^
					checksum
					);
			//(checks)
			if((calcChecksum != headerChecksum) || length < 0 || index < 0){	//no packet will be more than 16 MB
				saveQueue.offer(h1);
				saveQueue.offer(h2);
				saveQueue.offer(h3);
				saveQueue.offer(h4);
				saveQueue.offer(h5);
				saveQueue.offer(h6);
				saveQueue.offer(h7);
				saveQueue.offer(h8);
				saveQueue.offer(h9);
				continue;	//keep trying
			}
			//--Match Data
			//(get data)
			byte[] data = new byte[length];
			calcChecksum = 0x0;
			for(int i=0; i<data.length; i++){
				b = saveQueue.isEmpty() ? byteIter.next() : saveQueue.poll();
				calcChecksum = (byte) (calcChecksum ^ b);
				data[i] = b;
			}
			//(checksum)
			if(calcChecksum != checksum){
				for(int i=0; i<data.length; i++){
					saveQueue.offer(data[i]);
				}
				continue;	//keep trying
			}
			
			//--Success! We have a match
			Packet p = new Packet();
			p.type = type;
			p.length = length;
			p.index = index;
			p.checksum = checksum;
			p.data = data;
			return Pair.make(p, saveQueue);	//success!
		}
	}

	private Packet readPacket(Queue<Byte> overBuffer) throws CorruptionError{
		int overBufferLen = overBuffer == null ? 0 : overBuffer.size();
		try {
			if(overBufferLen == 0){ ensureData(TIMEOUT*1000000L); }
			//(check if we got input)
			if(overBufferLen == 0 && receive.available() == 0){
				return null;
			}
			//(read header)
			Packet p = new Packet();
			if(!ensureReadable(Math.max(PacketLen - overBufferLen, 0))){
				throw new CorruptionError(); //case: could not read the whole packet
			}
			byte type = (byte) ((overBuffer != null && !overBuffer.isEmpty()) ? overBuffer.poll() : receive.read());
			for(Type t : Type.values()){
				if(t.code == type){
					p.type = t;
				}
			}
			if(p.type == null){ throw new CorruptionError(); }
			p.length = readInt(overBuffer);
			p.index = readInt(overBuffer);
			p.checksum = (byte) ((overBuffer != null && !overBuffer.isEmpty()) ? overBuffer.poll() : receive.read());
			p.headerChecksum = (byte) ((overBuffer != null && !overBuffer.isEmpty()) ? overBuffer.poll() : receive.read());
			//(check header)
			byte checksum = (byte) (0 ^ p.type.code ^ 
						((p.index>>0)|0xFF) ^ 
						((p.index>>8)|0xFF) ^
						((p.index>>16)|0xFF) ^
						((p.index>>24)|0xFF) ^
						((p.length>>0)|0xFF) ^
						((p.length>>8)|0xFF) ^
						((p.length>>16)|0xFF) ^
						((p.length>>24)|0xFF) ^
						p.checksum
						);
			if(checksum != p.headerChecksum){
				throw new CorruptionError();
			}
			//(read data)
			p.data = new byte[p.length];
			int overBufferLenLeft = Math.max(0, overBufferLen-PacketLen);
			ensureReadable(Math.max(0, p.length-overBufferLenLeft));
			for(int i=0; i<Math.min(overBufferLenLeft, p.length); i++){
				p.data[i] = overBuffer.poll();
			}
			receive.read(p.data, overBufferLenLeft, p.data.length);
			//(check data)
			checksum = 0;
			for(byte b : p.data){
				checksum = (byte) (checksum ^ b);
			}
			if(checksum != p.checksum){
				throw new CorruptionError();
			}
			Log.log(Thread.currentThread().getName() + " Read packet (type=" + p.type + ")");
			return p;
		} catch (IOException e) {
			return null;
		}
	}
	
	private void sendPacket(Packet p){
		try {
			byte[] toSend = new byte[PacketLen+p.data.length];
			toSend[0] = p.type.code;
			writeInt(p.length, toSend, 1);
			writeInt(p.index, toSend, 5);
			toSend[9] = p.checksum;
			toSend[10] = p.headerChecksum;
			System.arraycopy(p.data, 0, toSend, PacketLen, p.data.length);
			send.write(toSend);
			Log.log(Thread.currentThread().getName() + " Send packet (type=" + p.type +")");
		} catch (IOException e) {
			e.printStackTrace();
			//noisy channel; we failed at writing
		}
	}
	
	private void sendPacket(Type type, byte[] data, int length){
		sendPacket(type, data, length, -1);
	}
	
	private void sendPacket(Type type, byte[] data, int length, int index){
		Packet p = new Packet();
		if(data == null){ data = new byte[0]; }
		p.type = type;
		if(index >= 0) p.index = index;
		else p.index = nextIndex.getAndIncrement();
		if(p.type == Type.DAT){ lastSent = p.index; }
		p.length = length;
		p.data = data;
		//(data checksum)
		byte checksum = 0x0;
		for(byte b : data){
			checksum = (byte) (checksum ^ b);
		}
		p.checksum = checksum;
		//(header checksum)
		byte headerChecksum = (byte) (0 ^ p.type.code ^ 
				((p.index>>0)|0xFF) ^ 
				((p.index>>8)|0xFF) ^
				((p.index>>16)|0xFF) ^
				((p.index>>24)|0xFF) ^
				((p.length>>0)|0xFF) ^
				((p.length>>8)|0xFF) ^
				((p.length>>16)|0xFF) ^
				((p.length>>24)|0xFF) ^
				p.checksum
				);
		p.headerChecksum = headerChecksum;
		sendPacket(p);
	}
	
	private void sendData(byte[] data, boolean newPacket){
		if(data != null){
			if(newPacket){
				sendPacket(Type.DAT, data, data.length);
			}else{
				sendPacket(Type.DAT, data, data.length, lastSent);
			}
		}
	}
	
	private void handlePacket(Packet p){
		lastRcvd = p.index;
		queueLock.lock();
		receiveQueue.add(p.data);
		packetReady.signalAll();
		queueLock.unlock();
	}

	private void changeState(State state){
		//(change state)
		State lastState = this.state;
		this.state = state;
		//(signal connected)
		if(	(lastState != State.ESTABLISHED && state == State.ESTABLISHED) ||
			(lastState != State.CLOSED && lastState != State.NOT_RUNNING &&  state == State.CLOSED) ){
			connLock.lock();
			connectStatusChanged.signalAll();
			connLock.unlock();
		}
		//(debug)
		if(state != lastState) Log.log(Thread.currentThread().getName() + " Changed State: " + state);
	}
	
	private Runnable dequeueTask(){
		userActionsLock.lock();
		if(userActions.isEmpty()){
			userActionsLock.unlock();
			return null;
		}else{
			Runnable rtn = userActions.poll();
			userActionsLock.unlock();
			return rtn;
		}
	}
	
	private void queueTask(Runnable r){
		userActionsLock.lock();
		userActions.offer(r);
		userActionsLock.unlock();
		if(inputStreamDataReady != null){ inputStreamLock.lock(); inputStreamDataReady.signalAll(); inputStreamLock.unlock(); }
	}

	private final void protocolError(){
		changeState(State.BROKEN);
		sendPacket(Type.FIN, null, 0);
	}
	
	@Override
	public void run(){
		changeState(State.CLOSED);
		connLock.lock();
		running.signalAll();
		connLock.unlock();
		Packet received = null;
		Queue<Byte> overBuffer = null;
		while(shouldRun){
			//--User Action(s)
			Runnable userTask = null;
			while((userTask = dequeueTask()) != null){
				userTask.run();
			}
			if(!shouldRun){ break; }	//user prompted exit
			
			//--Get Packet
			switch(state){
			case SYN_RCVD:
				break;
			default:
				try {
					received = readPacket(overBuffer);
					if(overBuffer != null && overBuffer.isEmpty()){ overBuffer = null; }
//					if(received != null) Log.log(Thread.currentThread().getName() + " Packet Received: " + received.type);
				} catch (CorruptionError e) {
//					Log.log(Thread.currentThread().getName() + " Corrupted Packet!");
					// a packet was sent, but didn't show
					// up well. This means it'll be re-sent soon
					Pair<Packet,Queue<Byte>> pair = readCorruptedPacket();
					received = pair.car();
//					Log.log(Thread.currentThread().getName() + " Corrupted Packet resolved to " + received.type);
					overBuffer = pair.cdr();
				}
				break;
			}
			//--Handle Packet
			switch(state){
			case CLOSED:
				if(received != null){
					switch(received.type){
					case SYN:
						changeState(State.SYN_RCVD);
						break;
					case SYNACK:
						protocolError();
						break;
					case DAT:
						protocolError();
						break;
					case ACK:
						protocolError();
						break;
					case STP:
						protocolError();
						break;
					case FIN:
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						protocolError();
						break;
					}
				}
				break;
	
			case SYN_SENT:
				if(received != null){
					switch(received.type){
					case SYN:
						protocolError();
						break;
					case SYNACK:
						changeState(State.ESTABLISHED);
						break;
					case DAT:
						sendPacket(Type.SYN, null, 0);
						break;
					case ACK:
						protocolError();
						break;
					case STP:
						sendPacket(Type.SYN, null, 0);
						break;
					case FIN:
						sendPacket(Type.SYN, null, 0);
						break;
					case FINACK:
						protocolError();
						break;
					}
				}else{
					sendPacket(Type.SYN, null, 0);
				}
				break;
	
			case SYN_RCVD:
				sendPacket(Type.SYNACK, null, 0);
				changeState(State.ESTABLISHED);
				break;
	
			case ESTABLISHED:
				if(received != null){
					switch(received.type){
					case SYN:
						sendPacket(Type.SYNACK, null, 0);
						break;
					case SYNACK:
						protocolError();
						break;
					case DAT:
						if(received.index == lastRcvd){
							sendPacket(Type.ACK, null, 0);
						}else{
							handlePacket(received);
							sendPacket(Type.ACK, null, 0);
						}
						break;
					case ACK:
						queueLock.lock();
						sendQueue.poll();
						sendData(sendQueue.peek(), true);
						queueLock.unlock();
						break;
					case STP:
						changeState(State.STP_RCVD);
						break;
					case FIN:
						changeState(State.CLOSED);
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						protocolError();
						break;
					}
				}else{
					//resend last packet
					queueLock.lock();
					sendData(sendQueue.peek(), false);
					queueLock.unlock();
				}
				break;
	
			case STP_SENT:
				if(received != null){
					switch(received.type){
					case SYN:
						sendPacket(Type.SYNACK, null, 0);
						break;
					case SYNACK:
						protocolError();
						break;
					case DAT:
						sendPacket(Type.STP, null, 0);
						break;
					case ACK:
						changeState(State.CLOSING);
						sendPacket(Type.FIN, null, 0);
						break;
					case STP:
						changeState(State.CLOSING);
						sendPacket(Type.FIN, null, 0);
						break;
					case FIN:
						changeState(State.CLOSED);
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						protocolError();
						break;
					}
				}else{
					//resend last packet
					queueLock.lock();
					
					sendData(sendQueue.peek(), false);
					queueLock.unlock();
				}
				break;
	
			case STP_RCVD:
				if(received != null){
					switch(received.type){
					case SYN:
						protocolError();
						break;
					case SYNACK:
						protocolError();
						break;
					case DAT:
						if(received.index == lastRcvd){
							sendPacket(Type.ACK, null, 0);
						}else{
							handlePacket(received);
							sendPacket(Type.ACK, null, 0);
						}
						break;
					case ACK:
						//protocol error
						changeState(State.BROKEN);
						sendPacket(Type.FIN, null, 0);
						break;
					case STP:
						break;
					case FIN:
						changeState(State.CLOSED);
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						protocolError();
						break;
					}
				}
				break;
	
			case CLOSING:
				if(received != null){
					switch(received.type){
					case SYN:
						sendPacket(Type.SYNACK, null, 0);
						break;
					case SYNACK:
						protocolError();
						break;
					case DAT:
						sendPacket(Type.FIN, null, 0);
						break;
					case ACK:
						protocolError();
						break;
					case STP:
						sendPacket(Type.FIN, null, 0);
						break;
					case FIN:
						changeState(State.CLOSED);
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						changeState(State.CLOSED);
						break;
					}
				}else{
					sendPacket(Type.FIN, null, 0);
				}
				break;
	
				//(try to recover from broken state)
			case BROKEN:
				if(received != null){
					switch(received.type){
					case SYN:
						changeState(State.ESTABLISHED);  //exit to re-established
						sendPacket(Type.SYNACK, null, 0);
						break;
					case SYNACK:
						sendPacket(Type.FIN, null, 0);
						break;
					case DAT:
						sendPacket(Type.FIN, null, 0);
						break;
					case ACK:
						sendPacket(Type.FIN, null, 0);
						break;
					case STP:
						sendPacket(Type.FIN, null, 0);
						break;
					case FIN:
						changeState(State.CLOSED);  //exit to closed
						sendPacket(Type.FINACK, null, 0);
						break;
					case FINACK:
						changeState(State.CLOSED);  //exit to closed
						break;
					}
				}
				break;
	
			default:
				//Unknown state: close the connection
				changeState(State.BROKEN);
				sendPacket(Type.FIN, null, 0);
				break;
			}
		}
	}

	public boolean connect(){
		connLock.lock();	//ensure we don't connect behind this function call's back
		queueTask(new Runnable(){
			@Override
			public void run(){
				connLock.lock();
				switch(state){
				case CLOSED:
					//--Connect
					changeState(State.SYN_SENT);
					sendPacket(Type.SYN, null, 0);
					break;
				default:
					connectStatusChanged.signalAll();
					break;
				}
				connLock.unlock();
			}
		});
		//--Wait for Connection
		try {
			connectStatusChanged.awaitNanos(CONN_TIMEOUT*1000000L);
		} catch (InterruptedException e) {}
		connLock.unlock();
		//--Check if Connected
		return state == State.ESTABLISHED;
	}
	
	public boolean disconnect(){
		connLock.lock();
		queueTask(new Runnable(){
			@Override
			public void run(){
				connLock.lock();
				switch(state){
				case ESTABLISHED:
					queueLock.lock();
					if(sendQueue.isEmpty()){
						state = State.CLOSING;
						sendPacket(Type.FIN, null, 0);
					}else{
						state = State.STP_SENT;
						sendPacket(Type.STP, null, 0);
					}
					break;
				case CLOSED:
					connectStatusChanged.signalAll();
					break;
				default:
					//protocol error
					changeState(State.BROKEN);
					sendPacket(Type.FIN, null, 0);
					break;
				}
				connLock.unlock();
			}
		});
		//--Wait for Connection
		try {
			connectStatusChanged.awaitNanos(CONN_TIMEOUT*1000000L);
		} catch (InterruptedException e) {}
		connLock.unlock();
		return state == State.CLOSED;
	}
	
	public void close(){
		queueTask(new Runnable(){
			@Override
			public void run(){
				shouldRun = false;
			}
		});
	}

	public boolean isValid(){
		return state != State.BROKEN;
	}

	public boolean send(final byte[] bytes){
		if(state != State.ESTABLISHED){
			return false;
		}
		queueLock.lock();
		sendQueue.add(bytes);
		if(sendQueue.size() == 1){
			sendData(sendQueue.peek(), true);
		}
		queueLock.unlock();
		return true;
	}

	public byte[] receive(){
		queueLock.lock();
		while(receiveQueue.size() == 0){
			packetReady.awaitUninterruptibly();
		}
		byte[] rtn = receiveQueue.poll();
		queueLock.unlock();
		return rtn;
	}
}
