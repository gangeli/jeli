package org.goobs.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import gnu.io.CommPortIdentifier; 
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent; 
import gnu.io.SerialPortEventListener; 
import gnu.io.UnsupportedCommOperationException;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.goobs.exec.ExitCode;
import org.goobs.exec.Option;
import org.goobs.scheme.ExternalPrimitive;
import org.goobs.scheme.Scheme;
import org.goobs.scheme.SchemeException;

import static org.goobs.exec.Log.*;

public class Arduino implements SerialPortEventListener{

	private static final byte MSG_HEADER = 1;
	private static final byte MSG_FOOTER = 4;
	
	private static final byte HIGH = 1;
	private static final byte LOW = 0;
	public static enum Command{
		PIN_DIGITAL((byte) 1),
		PWM((byte) 2),
		ANALOG_READ((byte) 3),
		;
		
		private final byte code;
		Command(byte code){
			this.code = code;
		}
	}
	

	/** Milliseconds to block while waiting for port open */
	@Option(name="arduinoTimeout")
	private static final int TIME_OUT = 2000;
	/** Default bits per second for COM port. */
	@Option(name="arduinoDataRate")
	private static final int DATA_RATE = 9600;
	/** The next Arduino instance */
	private static final AtomicInteger nextInstance = new AtomicInteger(0);

	/** The name of this app, for the serial port */
	private String appName;
	/** The name of the port the Arduino is connected on */
	private String portName;
	/** The port the Arduino is connected on */
	private SerialPort port;
	
	private InputStream input;
	private OutputStream output;
	
	private List<ArduinoListener> listeners = new LinkedList<ArduinoListener>();

	public Arduino(){
		this(null);
	}

	public Arduino(String portPath){
		this.portName = portPath;
		this.appName = this.getClass().getName() + nextInstance.getAndIncrement();
	}

	public void addListener(ArduinoListener listener){
		listeners.add(listener);
	}
	
	public void removeListener(ArduinoListener listener){
		listeners.remove(listener);
	}

	@SuppressWarnings("unchecked")
	public void connect() {
		//--Get Comm Port
		//(get port identifier)
		CommPortIdentifier portId = null;
		Enumeration <CommPortIdentifier> portEnum = (Enumeration<CommPortIdentifier>) CommPortIdentifier.getPortIdentifiers();
		if(portName == null){
			if(!portEnum.hasMoreElements()){
				throw fail("Cannot connect to Arduino: No device connected");
			}else{
				portId = portEnum.nextElement();
				portName = portId.getName();
			}
		}else{
			StringBuilder ids = new StringBuilder(); boolean first = true;
			while (portEnum.hasMoreElements()) {
				CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
				if(!first){ ids.append(", "); }
				ids.append(currPortId.getName()); first = false;
				if(currPortId.getName().equals(portName)){
					portId = currPortId;
				}
			}
			if(portId == null){
				throw fail("No serial port found at path: " + portName + ". valid ports={" + ids + "}");
			}
		}
		assert portId != null;
		assert portName != null;

		try {
			//(open serial port)
			port = (SerialPort) portId.open(appName, TIME_OUT);
			//(set port parameters)
			port.setSerialPortParams(DATA_RATE,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);
			//(open the streams)
			input = port.getInputStream();
			output = port.getOutputStream();
			port.addEventListener(this);
			port.notifyOnDataAvailable(true);
		} catch (PortInUseException e) {
			throw fail("Cannot connect to Arduino: Port " + portName + " is already in use");
		} catch (UnsupportedCommOperationException e) {
			throw fail(e);
		} catch (IOException e) {
			throw fail(e);
		} catch (TooManyListenersException e) {
			throw fail(e);
		}
	}

	/**
	 * This should be called when you stop using the port.
	 * This will prevent port locking on platforms like Linux.
	 */
	public synchronized void close() {
		if (port != null) {
			port.removeEventListener();
			port.close();
			port = null;
		}
	}
	
	/*
	 * Handle Arduino Data Streams
	 */
	private Queue <byte[]> dataChunks = new LinkedList<byte[]>();
	private Iterator <byte[]> dataDigester = new Iterator <byte[]> (){
		private byte[] pendingChunk = new byte[0];
		private int pendingIndex = 0;
		private List<Byte> pendingBuilder = new ArrayList<Byte>();
		private byte[] pending = null;
		private int inData = -1;
		private int toReadLength = -1;
		@Override
		public boolean hasNext() {
			if(pending != null){ return true; }	//String waiting
			byte b = (byte) 0x0;
			while(true){
				//(get a byte)
				while(pendingIndex >= pendingChunk.length){
					if(dataChunks.isEmpty()){ return false; }	//No more info
					pendingChunk = dataChunks.poll();
					pendingIndex = 0;
				}
				b = pendingChunk[pendingIndex];
				pendingIndex += 1;
				//(cases)
				if(inData < 0 && b == MSG_HEADER){
					//case: start data
					inData = 0;
				}else if(inData == 0){
					//case: read length
					toReadLength = b;
					inData += 1;
				}else if(inData > 0 && inData <= toReadLength){
					//case: data read
					pendingBuilder.add(b); 
					inData += 1;
					if(inData > toReadLength){
						//subcase: last byte read
						break;
					}
				}else{
					//case: byte is nothing special
				}
			}
			//(set return)
			pending = new byte[pendingBuilder.size()];
			for(int i=0; i<pending.length; i++){
				pending[i] = pendingBuilder.get(i);
			}
			//(clear state)
			inData = -1;
			toReadLength = -1;
			pendingBuilder = new ArrayList<Byte>();
			//(return)
			return true;
		}
		@Override
		public byte[] next() {
			if(!hasNext()){ throw new NoSuchElementException(); }
			byte[] rtn = pending;
			pending = null;
			return rtn;
		}
		@Override
		public void remove() {
			throw new NoSuchMethodError();
		}
	};
	private Queue <byte[]> lineChunks = new LinkedList<byte[]>();
	private Iterator <String> lineDigester = new Iterator <String> (){
		private byte[] pendingChunk = new byte[0];;
		private int pendingIndex = 0;
		private StringBuilder pendingBuilder = new StringBuilder();
		private String pending = null;
		@Override
		public boolean hasNext() {
			if(pending != null){ return true; }	//String waiting
			byte b = (byte) 0x0;
			do{
				while(pendingIndex >= pendingChunk.length){
					if(lineChunks.isEmpty()){ return false; }	//No more info
					pendingChunk = lineChunks.poll();
					pendingIndex = 0;
				}
				b = pendingChunk[pendingIndex];
				pendingIndex += 1;
				if(b != '\n'){ pendingBuilder.append(b); }
			} while(b != '\n');
			pending = pendingBuilder.toString();
			pendingBuilder = new StringBuilder();
			return true;
		}
		@Override
		public String next() {
			if(!hasNext()){ throw new NoSuchElementException(); }
			String rtn = pending;
			pending = null;
			return rtn;
		}
		@Override
		public void remove() {
			throw new NoSuchMethodError();
		}
	};
	
	/*
	 * Getting Messages from Arduino
	 */
	private byte[] pendingMessage = null;
	private Lock pendingMessageLock = new ReentrantLock();
	private Condition pendingMessageCond = pendingMessageLock.newCondition();
	private void receiveMessage(byte[] msg){
		pendingMessageLock.lock();
		if(pendingMessage != null){
			throw fail("Receiving another message without the first being read");
		}
		pendingMessage = msg;
		pendingMessageCond.signalAll();
		pendingMessageLock.unlock();
	}
	private byte[] retreiveMessage(){
		pendingMessageLock.lock();
		if(pendingMessage == null){
			try {
				pendingMessageCond.awaitNanos(2L*1000000000L);
			} catch (InterruptedException e) { }
		}
		byte[] rtn = pendingMessage;
		pendingMessage = null;
		pendingMessageLock.unlock();
		return rtn;
	}
	
	/**
	 * Handle an event on the serial port. Read the data and print it.
	 */
	@Override
	public synchronized void serialEvent(SerialPortEvent oEvent) {
		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				//(handle chunk)
				byte[] chunk = new byte[input.available()];
				input.read(chunk);
				for(ArduinoListener listener : listeners){
					listener.handleChunk(chunk);
				}
//				System.out.println(Thread.currentThread().getName() + " CHUNK> " + Arrays.toString(chunk));
				//(handle line)
				lineChunks.offer(chunk);
				while(lineDigester.hasNext()){
					String line = lineDigester.next();
					for(ArduinoListener listener : listeners){
						listener.handleLine(line);
					}
				}
				//(handle data)
				dataChunks.offer(chunk);
				while(dataDigester.hasNext()){
					byte[] data = dataDigester.next();
					receiveMessage(data);
				}
			} catch (IOException e) {
				for(ArduinoListener listener : listeners){
					listener.handleException(e);
				}
			}
		}
		// Ignore all the other eventTypes, but you should consider the other ones.
	}
	
	/*
	 * Scheme Interpreter
	 */
	
	@SuppressWarnings("unchecked")
	private List<Object> ensure(Object lst, String fxn, int numArgs){
		if(lst instanceof List){
			List<Object> rtn = (List<Object>) lst;
			if(rtn.size() != numArgs){
				throw new SchemeException("Expected " + numArgs + " arguments to " + fxn + "; found " + rtn.size() + " arguments");
			}
			return rtn;
		}else{
			throw new SchemeException("Expected a list of arguments to function " + fxn);
		}
	}

	public Scheme schemeInterpreter(){
		Scheme rtn = new Scheme();
		final ArduinoListener listener = new ArduinoListener(){
			@Override
			public void handleChunk(byte[] chunk) {
				//(do nothing)
			}
			@Override
			public void handleException(Exception e) {
				System.err.println("Uncaught exception from Arduino: " + e.getClass().getName() + ": " + e.getMessage());
				exit(ExitCode.FATAL_EXCEPTION);
			}
			@Override
			public void handleLine(String str) {
				System.out.println("(from Arduino): " + str);
			}
		};
		this.addListener(listener);
		//--Remove Listener
		rtn.addPrimitive("remove-listener", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				ensure(arg, "remove-listener", 0);
				Arduino.this.removeListener(listener);
				return "ok";
			}
		});
		//--Add Listener
		rtn.addPrimitive("add-listener", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				ensure(arg, "add-listener", 0);
				Arduino.this.addListener(listener);
				return "ok";
			}
		});
		//--Pin High
		rtn.addPrimitive("pin-high", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				Object obj = ensure(arg, "pin-high", 1).get(0);
				try {
					int pin = (Integer) obj;
					if(pin < 0 || pin > 13){
						throw new SchemeException("Invalid digital port: " + port);
					}
					output.write(new byte[]{Command.PIN_DIGITAL.code, (byte) pin, HIGH, 0x0, 0x0});
				} catch (ClassCastException e1) {
					throw new SchemeException("Non-integer pin for pin-high: " + obj);
				} catch (IOException e) {
					return false;
				}
				return true;
			}
		});
		//--Pin Low
		rtn.addPrimitive("pin-low", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				Object obj = ensure(arg, "pin-low", 1).get(0);
				try {
					int pin = (Integer) obj;
					if(pin < 0 || pin > 13){
						throw new SchemeException("Invalid digital port: " + port);
					}
					output.write(new byte[]{Command.PIN_DIGITAL.code, (byte) pin, LOW, 0x0, 0x0});
				} catch (ClassCastException e1) {
					throw new SchemeException("Non-integer pin for pin-low: " + obj);
				} catch (IOException e) {
					return false;
				}
				return true;
			}
		});
		//--PWM
		rtn.addPrimitive("pwm", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				List<Object> args = ensure(arg, "pwm", 2);
				try {
					//(variables)
					int pin = (Integer) args.get(0);
					int freq = (Integer) args.get(1);
					//(error checks)
					if(pin != 3 && pin != 5 && pin != 6 && pin != 9 && pin != 10 && pin != 11){
						throw new SchemeException("Invalid pin for PWM: " + pin);
					}
					if(freq < 0 || freq > 255){
						throw new SchemeException("PWM frequency must be between 0 and 255: " + freq);
					}
					//(write)
					output.write(new byte[]{Command.PWM.code, (byte) pin, (byte) freq, 0x0, 0x0});
				} catch (ClassCastException e1) {
					throw new SchemeException("Non-integer arguments to pwm: " + args.get(0) + " and " + args.get(1));
				} catch (IOException e) {
					return false;
				}
				return true;
			}
		});
		//--Read Analog
		rtn.addPrimitive("read-analog", new ExternalPrimitive(){
			private static final long serialVersionUID = 4530103559815172221L;
			@Override
			public Object apply(Object arg) {
				List<Object> args = ensure(arg, "read-analog", 1);
				try {
					//(variables)
					int pin = (Integer) args.get(0);
					//(error checks)
					if(pin < 0 || pin > 5){
						throw new SchemeException("Invalid pin for analog read: " + pin);
					}
					//(request reading)
					output.write(new byte[]{Command.ANALOG_READ.code, (byte) pin, 0x0, 0x0, 0x0});
					//(get response)
					byte[] response = retreiveMessage();
					if(response == null){ return false; }
					//(format response)
					if(response.length != 4){ throw new SchemeException("Invalid response from Arduino to read-pin"); }
					Integer rtn = response[0] << 24 | response[1] << 16 | response[2] << 8 | response[3];
					return rtn;
				} catch (ClassCastException e1) {
					throw new SchemeException("Non-integer arguments to pwm: " + args.get(0) + " and " + args.get(1));
				} catch (IOException e) {
					return false;
				}
			}
		});

		return rtn;
	}

	@SuppressWarnings("unchecked")
	public static final String findPort(){
		Enumeration <CommPortIdentifier> portEnum = (Enumeration<CommPortIdentifier>) CommPortIdentifier.getPortIdentifiers();
		if(!portEnum.hasMoreElements()){
			throw fail("Cannot connect to Arduino: No device connected");
		}else{
			return portEnum.nextElement().getName();
		}
	}

	public static void main(String[] args) throws Exception {
		final Arduino device = new Arduino("/dev/ttyUSB0");
		device.connect();
		Scheme s = device.schemeInterpreter();
		s.addExitTask(new Runnable(){
			public void run(){
				device.close();
			}
		});
		s.interactive("arScm>");
	}
}
