package org.goobs.stanford;

import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;
import org.goobs.exec.Execution;
import org.goobs.exec.ExitCode;

import edu.stanford.nlp.util.logging.Redwood.Util;

import java.io.IOException;
import java.util.Properties;

public class StanfordExecutionLogInterface extends Execution.LogInterface {

	@Override protected void bootstrap(){
		Redwood.hideOnlyChannels(Redwood.DBG);
	}

	@Override
	public void setup(){
		//(tweak stanford)
		Properties props = new Properties();
		props.setProperty("log.collapse", "approximate");
		try {
			props.setProperty("log.file",Execution.touch("log").getPath());
		} catch (IOException e) {
			Util.err("Could not create log file: " + e.getMessage());
		}
		props.setProperty("log.neatExit", "true");
		props.setProperty("log.console.trackStyle", "BOLD");
		//(init stanford)
		StanfordRedwoodConfiguration.apply(props);
		//(tweaks)
		Redwood.hideOnlyChannels(Redwood.DBG);
	}

	@Override
	protected void err(Object tag, Object obj){
		Util.err(tag,obj);
	}
	protected void log(String tag, Object obj){
		Util.log(tag,obj);
	}
	protected void debug(String tag, Object obj){
		Util.debug(tag,obj);
	}
	protected void warn(String tag, Object obj){
		Util.warn(tag, obj);
	}
	protected RuntimeException fail(Object msg){
		return Util.fail(msg);
	}
	protected void exit(ExitCode code){
		Util.exit(code.code);
	}
	protected void startTrack(String name){
		Util.startTrack(name);
	}
	protected void endTrack(String check){
		Util.endTrack(check);
	}
    protected void exception(Exception e){
        Util.log(e);
    }
}
