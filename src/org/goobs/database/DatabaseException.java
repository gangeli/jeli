package org.goobs.database;

public class DatabaseException extends RuntimeException{
	private static final long serialVersionUID = -4453764286217239358L;
	public DatabaseException(){ super(); }
	public DatabaseException(String str){ super(str); }
	public DatabaseException(Throwable e){ super(e); }
}
