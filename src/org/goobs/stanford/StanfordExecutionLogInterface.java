package org.goobs.stanford;

import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodSetup;
import org.goobs.exec.Execution;
import org.goobs.exec.ExitCode;

import edu.stanford.nlp.util.logging.Redwood.Static;

import java.io.IOException;
import java.util.Properties;

public class StanfordExecutionLogInterface extends Execution.LogInterface {

	@Override protected void bootstrap(){
		Redwood.hideChannel(Redwood.DBG);
		Redwood.dontPrintChannels();
	}

	@Override
	public void setup(){
		//(tweak stanford)
		Properties props = new Properties();
		props.setProperty("log.collapse", "approximate");
		try {
			props.setProperty("log.file",Execution.touch("log").getPath());
		} catch (IOException e) {
			Static.err("Could not create log file: " + e.getMessage());
		}
		props.setProperty("log.neatExit", "true");
		//(init stanford)
		StanfordRedwoodSetup.setup(props);
		//(tweaks)
		Redwood.dontPrintChannels();
		Redwood.hideChannel(Redwood.DBG);
	}

	@Override
	protected void err(Object tag, Object obj){
		Static.err(tag,obj);
	}
	protected void log(String tag, Object obj){
		Static.log(tag,obj);
	}
	protected void debug(String tag, Object obj){
		Static.debug(tag,obj);
	}
	protected void warn(String tag, Object obj){
		Static.warn(tag, obj);
	}
	protected RuntimeException fail(Object msg){
		return Static.fail(msg);
	}
	protected void exit(ExitCode code){
		Static.exit(code.code);
	}
	protected void startTrack(String name){
		Static.startTrack(name);
	}
	protected void endTrack(String check){
		Static.endTrack(check);
	}
}
