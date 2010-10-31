package org.goobs.exec;

public enum ExitCode {
	OK(0),
	INVALID_ARGUMENTS(1),
	BAD_OPTION(2),
	VOLUNTARY(3),
	FATAL_EXCEPTION(4),
	
	UNKNOWN(999)
	;
	
	public final int code;
	ExitCode(int code){
		this.code = code;
	}
}