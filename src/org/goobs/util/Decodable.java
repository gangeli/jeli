package org.goobs.util;

import java.lang.reflect.Type;

public interface Decodable {
	public static final char ESCAPE_CHAR = '\\';
	public Decodable decode(String encoded, Type[] typeParams);
	public String encode();
}
