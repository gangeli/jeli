package org.goobs.scheme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class NetPrimitive extends ScmObject implements Primitive {
	/**
	 * 
	 */
	private static final long serialVersionUID = 585805031569696674L;
	
	protected static String NETSEND = "net-send";
	protected static String NETRECV = "net-recv";

	private String id;
	//private Scheme scm;

	public NetPrimitive(String i, Scheme s) {
		super("#[subr " + i + "]", ScmObject.FUNCTION);
		id = i;
		//scm = s;
	}

	@Override
	public ScmObject apply(ScmObject args, SchemeThreadPool.SchemeThread thread) throws SchemeException {
		if (id.equals(NetPrimitive.NETSEND)) {
			return send(args);
		} else if (id.equals(NetPrimitive.NETRECV)) {
			return recv(args);
		} else {
			throw new SchemeException("[INTERNAL]: cannot find primitive: "
					+ id);
		}
	}
	
	private ScmObject send(ScmObject args){
		//error checks
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject target = args.car();
		if(!target.isType(ScmObject.PAIR)){
			throw new SchemeException("invalid target");
		}
		if(target.car().isNil() || target.cdr().isNil()){
			throw new SchemeException("target address or port is nil");
		}
		ScmObject content = args.cdr().car();
		//variables
		ScmObject addr = target.car();
		ScmObject prt = target.cdr();
		if(!addr.isType(ScmObject.STRING)){ throw new SchemeException("address must be a string"); }
		if(!prt.isType(ScmObject.INTEGER)){ throw new SchemeException("port must be an integer"); }
		String address = (String) addr.getContent();
		int port = (Integer) prt.getContent();
		//exceptions possible...
		DatagramSocket socket = null;
		try {
			//object serialization
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			ObjectOutputStream objCruncher = new ObjectOutputStream(stream);
			objCruncher.writeObject(content);
			//connect
			byte[] buffer = stream.toByteArray();
			socket = new DatagramSocket();
			socket.connect(new InetSocketAddress(address, port));
			DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
			socket.send(packet);
			socket.disconnect();
			socket.close();
			return new ScmObject("okay", ScmObject.WORD);
		} catch (SocketException e) {
			if(socket != null) { socket.disconnect(); socket.close(); }
			throw new SchemeException("socket exception on send: " + e.getMessage());
		} catch (IOException e) {
			if(socket != null) { socket.disconnect(); socket.close(); }
			throw new SchemeException("io exception on send: " + e.getMessage());
		}
	}
	
	private ScmObject recv(ScmObject args){
		//error checks
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject prt = args.car();
		if(!prt.isType(ScmObject.INTEGER)){
			throw new SchemeException("argument to recv must be an integer port");
		}
		//variables
		int port = (Integer) prt.getContent();
		//exceptions possible...
		DatagramSocket socket = null;
		try {
			//connect
			byte[] buffer = new byte[512];
			socket = new DatagramSocket(port);
			DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
			socket.receive(packet);
			//read object
			ByteArrayInputStream stream = new ByteArrayInputStream(buffer);
			ObjectInputStream mother = new ObjectInputStream(stream);
			Object cand = mother.readObject();
			socket.disconnect();
			socket.close();
			if(!(cand instanceof ScmObject)){
				throw new SchemeException("data is not a scheme object");
			}
			return (ScmObject) cand;
		} catch (SocketException e) {
			if(socket != null) { socket.disconnect(); socket.close(); }
			throw new SchemeException("socket exception on recv: " + e.getMessage());
		} catch (IOException e) {
			if(socket != null) { socket.disconnect(); socket.close(); }
			throw new SchemeException("io exception on recv: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			if(socket != null) { socket.disconnect(); socket.close(); }
			throw new SchemeException("invalid data recieved");
		}
	}
}
