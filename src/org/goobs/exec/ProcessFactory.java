package org.goobs.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ProcessFactory {

	public static final int NO_TIMEOUT = -1;
	public static final ProcessListener defaultListener = new ProcessListener() {
		@Override
		public void handleErrorLine(String msg) {
			System.err.println(msg);
		}

		@Override
		public void handleOutputLine(String msg) {
			System.out.println(msg);
		}

		@Override
		public void onException(Exception e) {
			e.printStackTrace();
		}

		@Override
		public void onFinish(int returnVal) {
			System.out.println("[" + returnVal + "] DONE");
		}

		@Override
		public void onTimeout() {
			System.err.println("DONE (timeout)");
		}
	};

	/*
	 * Variables
	 */
	private Runtime runner;

	private OperatingSystem os;
	@SuppressWarnings("unused")
	private String architecture;
	private String fileSeperator;
	private HashMap<String, String> env;
	private File wd;

	private static final ProcessListener dummy = new ProcessListener() {
		@Override
		public void handleErrorLine(String msg) {
		}

		@Override
		public void handleOutputLine(String msg) {
		}

		@Override
		public void onException(Exception e) {
		}

		@Override
		public void onFinish(int returnVal) {
		}

		@Override
		public void onTimeout() {
		}
	};

	public static final class ProcessTimeoutException extends RuntimeException {
		private static final long serialVersionUID = -7115716123966661496L;

		private ProcessTimeoutException() {
		}

		public String getMessage() {
			return "";
		}
	}

	private static final class StreamGobbler extends Thread {
		private InputStream is;
		private String type;
		private ProcessListener listener;
		
		private Lock doneLock = new ReentrantLock();
		private Condition doneCond = doneLock.newCondition();
		private boolean done = false;

		private StreamGobbler(InputStream is, String type,
				ProcessListener listener) {
			this.is = is;
			this.type = type;
			this.listener = listener;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					if (type.equals("ERROR")) {
						listener.handleErrorLine(line);
					} else {
						listener.handleOutputLine(line);
					}
				}
			} catch (IOException ioe) {
				listener.onException(ioe);
			}
			doneLock.lock();
			done = true;
			doneCond.signalAll();
			doneLock.unlock();
		}
		
		public void flush(){
			doneLock.lock();
			while(!done){
				doneCond.awaitUninterruptibly();
			}
			doneLock.unlock();
		}
	}

	/*
	 * Constructors
	 */
	public ProcessFactory() {
		resetDefaults();
	}

	/*
	 * Methods
	 */

	public HashMap<String, String> getEnv() {
		return env;
	}

	public String setEnv(String key, String val) {
		return env.put(key, val);
	}

	public File getWorkingDir() {
		return wd;
	}

	public boolean setWorkingDir(File f) {
		if (!f.exists()) {
			return false;
		} else {
			this.wd = f;
			return true;
		}
	}

	public boolean setWorkingDir(String s) {
		s = s.replaceAll("/", this.fileSeperator);
		s = s.replaceAll("\\\\", this.fileSeperator);
		return setWorkingDir(new File(s));
	}

	public void resetDefaults() {
		// --get system variables
		this.os = getOS();
		this.architecture = System.getProperty("os.arch");
		this.fileSeperator = System.getProperty("file.seperator");
		if (this.fileSeperator == null) {
			this.fileSeperator = "/";
		}
		// --set up runtime
		// (runtime)
		runner = Runtime.getRuntime();
		// (environment)
		this.env = new HashMap<String, String>();
		Map<String, String> defaultEnv = System.getenv();
		for (String key : defaultEnv.keySet()) {
			this.env.put(key, defaultEnv.get(key));
		}
		// (working directory)
		String dir = System.getProperty("user.dir");
		wd = new File(dir);
	}

	public int execute(String cmd) throws IOException {
		return execute(cmd, NO_TIMEOUT);
	}
	
	public int execute(String[] cmd) throws IOException {
		return execute(cmd, NO_TIMEOUT);
	}

	public int execute(String cmd, int timeout) throws IOException {
		return internalExecute(cmd, timeout, dummy);
	}
	
	public int execute(String[] cmd, int timeout) throws IOException {
		return internalExecute(cmd, timeout, dummy);
	}

	public void executeAsync(final String cmd, final ProcessListener listener) {
		executeAsync(cmd, NO_TIMEOUT, listener);
	}
	
	public void executeAsync(final String[] cmd, final ProcessListener listener) {
		executeAsync(cmd, NO_TIMEOUT, listener);
	}

	public void executeAsync(final String cmd, final int timeout,
			final ProcessListener listener) {
		new Thread() {
			@Override
			public void run() {
				try {
					int rtn = internalExecute(cmd, timeout, listener);
					listener.onFinish(rtn);
				} catch (Exception e) {
					if (e instanceof ProcessTimeoutException) {
						listener.onTimeout();
					} else {
						listener.onException(e);
					}
				}
			}
		}.start();
	}

	public void executeAsync(final String[] cmd, final int timeout,
			final ProcessListener listener) {
		new Thread() {
			@Override
			public void run() {
				try {
					int rtn = internalExecute(cmd, timeout, listener);
					listener.onFinish(rtn);
				} catch (Exception e) {
					if (e instanceof ProcessTimeoutException) {
						listener.onTimeout();
					} else {
						listener.onException(e);
					}
				}
			}
		}.start();
	}
	
	private int internalExecute(String cmd, int timeout,
			ProcessListener listener) throws IOException {
		// --Start as shell
		if (os == OperatingSystem.Linux || os == OperatingSystem.Unix) {
			//(create a temporary file)
			File f = File.createTempFile("processRunner", ".cmd");
			f.createNewFile();
			f.setExecutable(true);
			//(write the command to the file)
			FileWriter w = new FileWriter(f);
			w.append("#!/bin/sh\n");
			w.append(cmd.replaceAll("\n", " \\\n  "));
			w.flush();
			w.close();
			cmd = f.getAbsolutePath().replaceAll(" ", "\\ ");
			return internalExecute(new String[]{cmd}, timeout, listener);
		}else{
			throw new IllegalArgumentException("Running String commands not implemented in non-*nix systems");
		}
	}
	
	private int internalExecute(String[] cmd, int timeout, ProcessListener listener) throws IOException{
		int rtn = 0;
		Process p = runner.exec(cmd, createEnv(), wd);
		

		// --Create the stream gobblers
		StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(),
				"ERROR", listener);
		StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(),
				"OUTPUT", listener);
		// (kick them off)
		errorGobbler.start();
		outputGobbler.start();

		//--Run Process
		if (timeout < 0) {
			// --Case: no timeout
			try {
				rtn = p.waitFor();
			} catch (InterruptedException e) {
			}
		} else {
			// --Case: timeout
			try {
				Thread.sleep(timeout);
			} catch (InterruptedException e) {
			}
			try {
				rtn = p.exitValue();
			} catch (RuntimeException e) {
				timeout(p);
			}
		}
		
		//--Flush Streams
		outputGobbler.flush();
		errorGobbler.flush();
		
		//--Return
		return rtn;
	}

	private String[] createEnv() {
		String[] envp = new String[env.size()];
		int i = 0;
		for (String key : env.keySet()) {
			envp[i] = key + "=" + env.get(key);
			i++;
		}
		return envp;
	}

	private void timeout(Process p) {
		p.destroy();
		throw new ProcessTimeoutException();
	}

	public static String sanitize(String input) {
		// sanitize command
		// | & ; < > ( ) $ ` \ " '
		String cmd = input.replaceAll("\\|", "\\\\|");
		cmd = cmd.replaceAll("&", "\\\\&");
		cmd = cmd.replaceAll(";", "\\\\;");
		cmd = cmd.replaceAll("<", "\\\\<");
		cmd = cmd.replaceAll(">", "\\\\>");
		cmd = cmd.replaceAll("\\(", "\\\\\\(");
		cmd = cmd.replaceAll("\\)", "\\\\\\)");
		cmd = cmd.replaceAll("\\$", "\\\\\\$");
		cmd = cmd.replaceAll("`", "\\\\`");
		cmd = cmd.replaceAll("\"", "\\\\\"");
		cmd = cmd.replaceAll("'", "\\\\'");
		return cmd;
	}

	public static OperatingSystem getOS() {
		String os = System.getProperty("os.name").toLowerCase();
		OperatingSystem osVal;
		if (os.contains("windows")) {
			osVal = OperatingSystem.Windows;
		} else if (os.contains("linux")) {
			osVal = OperatingSystem.Linux;
		} else if (os.contains("unix") || os.contains("solaris")) {
			osVal = OperatingSystem.Unix;
		} else if (os.contains("mac")) {
			osVal = OperatingSystem.Mac;
		} else {
			throw new IllegalStateException("Unsupported operating system: "
					+ os);
		}
		return osVal;
	}

//	public static void main(String[] args) {
//		ProcessFactory fact = new ProcessFactory();
//		String cmd = "\"/usr/bin/vlc\" \"/home/gabor/entourage.avi\" --start-time 100";
//		fact.executeAsync(cmd, new ProcessListener() {
//			@Override
//			public void handleErrorLine(String msg) {
//				System.err.println(msg);
//			}
//			@Override
//			public void handleOutputLine(String msg) {
//				System.out.println(msg);
//			}
//			@Override
//			public void onException(Exception e) {
//				e.printStackTrace();
//			}
//
//			@Override
//			public void onFinish(int returnVal) {
//				System.out.println("DONE " + returnVal);
//			}
//
//			@Override
//			public void onTimeout() {
//				System.err.println("TIMEOUT");
//			}
//		});
//	}

}
