package org.goobs.exec;

import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.goobs.utils.ConfigFile;
import org.goobs.utils.Pair;

public final class ScriptRunner {

	private static String PERL = null;
	
	
	private static final void loadConfig(){
		ConfigFile config = ConfigFile.META_CONFIG;
		if(config.contains("PERL_PATH")) PERL = config.getString("PERL_PATH");
	}
	
	
	private static final Pair<String,String> run(String PROG, String filename, String[] args, InputStream stdin){
		if(PROG == null) throw new IllegalStateException("No path given for language");
		//--Setup
		//(process)
		ProcessFactory fact = new ProcessFactory();
		String[] allArgs = new String[args.length+2];
		allArgs[0] = PROG;
		allArgs[1] = filename;
		System.arraycopy(args, 0, allArgs, 2, args.length);
		//(returns)
		final StringBuilder stdOut = new StringBuilder();
		final StringBuilder stdErr = new StringBuilder();
		final Exception[] toThrow = new Exception[1];
		//(concurrency)
		final Lock pLock = new ReentrantLock();
		final Condition isDone = pLock.newCondition();
		final boolean[] done = new boolean[1]; done[0] = false;
		//--Execute
		fact.executeAsync(allArgs, new ProcessListener(){
			@Override
			public void handleErrorLine(String msg) {
				pLock.lock();
				stdErr.append(msg).append("\n");
				pLock.unlock();
			}
			@Override
			public void handleOutputLine(String msg) {
				pLock.lock();
				stdOut.append(msg).append("\n");
				pLock.unlock();
			}
			@Override
			public void onException(Exception e) {
				pLock.lock();
				toThrow[0] = null;
				done[0] = true;
				isDone.signalAll();
				pLock.unlock();
			}
			@Override
			public void onFinish(int returnVal) {
				pLock.lock();
				if(returnVal != 0){
					toThrow[0] = new IllegalStateException("Script exited with code: " + returnVal);
				}
				done[0] = true;
				isDone.signalAll();
				pLock.unlock();
			}
			@Override
			public void onTimeout() {
				pLock.lock();
				toThrow[0] = new IllegalStateException("Script timed out!");
				done[0] = true;
				isDone.signalAll();
				pLock.unlock();
			}
		});
		//--Wait For Completion
		pLock.lock();
		while(!done[0]){
			isDone.awaitUninterruptibly();
		}
		pLock.unlock();
		//--Process Output
		if(toThrow[0] != null){
			System.out.println("SCRIPT OUTPUT:");
			System.out.println(stdOut.toString());
			System.err.println(stdErr.toString());
			throw new RuntimeException(toThrow[0]);
		}
		//--Return
		return new Pair<String,String>(stdOut.toString(), stdErr.toString());
	}
	
	/**
	 * Run a perl script at the specified filename,
	 * returning the stdout and stderr output 
	 * @param filename The file to be run
	 * @return A pair of the (stdout, stderr) output of the script
	 */
	public static final Pair<String,String> runPerl(String filename){
		return runPerl(filename, new String[0], System.in);
	}
	
	/**
	 * Run a perl script at the specified filename,
	 * returning the stdout and stderr output 
	 * @param filename The file to be run
	 * @param args The command line arguments to the script
	 * @return A pair of the (stdout, stderr) output of the script
	 */
	public static final Pair<String,String> runPerl(String filename, String[] args){
		return runPerl(filename, args, System.in);
	}
	
	/**
	 * Run a perl script at the specified filename,
	 * returning the stdout and stderr output 
	 * @param filename The file to be run
	 * @param args The command line arguments to the script
	 * @param stdin The standard input to pass to the script process
	 * @return A pair of the (stdout, stderr) output of the script
	 */
	public static final Pair<String,String> runPerl(String filename, String[] args, InputStream stdin){
		if(PERL == null){
			loadConfig();
		}
		return run(PERL, filename, args, stdin);
	}
	
	public static void main(String[] args){
//		System.out.println( ConfigFile.class.getResourceAsStream("/lib.conf") );
		String out = ScriptRunner.runPerl("/home/gabor/tmp/eval.pl", new String[]{
			"-s", "/home/gabor/tmp/src.xml",
			"-r", "/home/gabor/tmp/ref.xml",
			"-t", "/home/gabor/tmp/tst.xml"
		}).car();
		System.out.println(out);
	}
	
}
