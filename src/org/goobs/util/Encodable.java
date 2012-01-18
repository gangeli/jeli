package org.goobs.util;

public class Encodable{
	public static final char LINE_BREAK = '#';
	public static final char TAB_BREAK = '%';

	public String encode() {
		String str = toString();
		str = str.replaceAll("\\n", "" + LINE_BREAK);
		str = str.replaceAll("\\t", "" + TAB_BREAK);
		//str = str.replaceAll(" +", " ");
		return str;
	}
	
}
