package org.goobs.util;


import org.goobs.exec.ProcessFactory;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ConfigFile {
	
	public static ConfigFile META_CONFIG = new ConfigFile(ConfigFile.class.getResourceAsStream("lib.conf"));
	
	static{
		try {
			META_CONFIG.read();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		switch(ProcessFactory.getOS()){
		case Windows:
			escapeChar = '^';
			break;
		default:
			escapeChar = '\\';
		}
	}
	
	private static final char escapeChar;
	private static final char listDelim = ',';
	private static final char listStart = '[';
	private static final char listEnd = ']';
	
	private char commentDelim = 0x00;
	private String path;
	private InputStream stream;
	
	private HashMap <String, Object> elements = new HashMap <String, Object> ();
	
	public ConfigFile(String path){
		this(path, '#');
	}
	
	public ConfigFile(String path, char commentDelim){
		this.path = path;
		this.commentDelim = commentDelim;
	}
	
	private ConfigFile(InputStream stream){
		this.stream = stream;
		this.commentDelim = '#';
	}
	
	
	
	private final String stripComments(String line){
		char[] chars = line.toCharArray();
		StringBuilder b = new StringBuilder();
		for(int i=0; i<chars.length; i++){
			if(chars[i] == commentDelim){
				return b.toString();
			}else{
				b.append(chars[i]);
			}
		}
		return b.toString();
	}
	
	private char[] readFileRaw() throws IOException{
		//--Check Input Stream
		if(this.stream != null){
			return readFileRaw(new BufferedReader(new InputStreamReader(this.stream)));
		}
		//--Get the file
		File f = new File(path);
		if(!f.exists()){
			throw new IOException("File does not exist: " + path);
		}
		return readFileRaw(new BufferedReader(new FileReader(f)));
	}
	
	private char[] readFileRaw(BufferedReader reader) throws IOException{
		//--Read the file
		String line = null;
		StringBuilder b = new StringBuilder();
		boolean isStart = true;
		while( (line=reader.readLine()) != null){
			String trimmed = stripComments(line).trim();
			if(isStart && trimmed.toLowerCase().matches("^import +[^=].*")){
				//(import another file)
				String path = line.replaceFirst("^import +", ""); //trim off the 'import' part of the line
				path = this.path.replaceAll("/[^/]*$","/") + path; //fix relative path issues
				b.append(new String( new ConfigFile(path, commentDelim).readFileRaw() ));
			}else{
				//(append the line normally)
				if(trimmed.length() > 0){
					isStart = false;
				}
				b.append(trimmed);
			}
			b.append("\n");
		}
		//--Return
		return b.toString().toCharArray();
	}
	
	private final void register(String name, Object value){
		elements.put(name.trim().toLowerCase(), value);
	}
	
	public void read() throws IOException {
		char[] file = readFileRaw();
		
		//--State Variables
		//(state)
		boolean isLeft = true;	//we start reading the left side
		boolean inList = false;	//we are not in a list term
		boolean inLiteral = false; //we are not in a literal string
		boolean lastWhitespace = true;
		//(remember)
		StringBuilder left = new StringBuilder();
		StringBuilder term = new StringBuilder();
		List <String> lst = new LinkedList <String>();
		//--FSA
		for(int i=0; i<file.length; i++){
			char c = file[i];
			if(isLeft){
				//--Variable Name
				if(inLiteral){
					throw new IOException("Variable name cannot contain single quotes");
				} else if(c == escapeChar){
					//(escaped character)
					i = handleEscape(file, i, left); 
					lastWhitespace = false;
				}else if(isWhitespace(c)){
					//(append a single space for whitespace)
					if(!lastWhitespace){
						left.append(' ');
					}
					lastWhitespace = true;
				}else if(c == '='){
					//(end of variable name)
					isLeft = false;
					lastWhitespace = true;
				}else{
					//(default)
					left.append(c);
					lastWhitespace = false;
				}
			}else if(inList){
				//--In a DBList
				if(c == escapeChar){
					//(escaped character)
					i = handleEscape(file, i, term);
					lastWhitespace = false;
				}else if(inLiteral){
					//(we are in a single-quoted string)
					if(c == '\''){
						inLiteral = false;
					}else{
						term.append(c);
					}
				}else if(c == '\''){
					inLiteral = true;
				}else if(isWhitespace(c)){
					//(append a single space for whitespace)
					if(!lastWhitespace){
						term.append(' ');
					}
					lastWhitespace = true;
				}else if(c == listDelim){
					//(end of list term)
					lst.add(term.toString().trim());
					term = new StringBuilder();
					lastWhitespace = true;
				}else if(c == listEnd){
					//(end of list)
					if(term.length() == 0){ 
						if(lst.size() > 0){
							throw new IOException("Null last list element (ends with a ','): " + left.toString());
						}
					}else{
						lst.add(term.toString().trim());
					}
					term = new StringBuilder();
					register(left.toString(), lst);
					left = new StringBuilder();
					lst = new LinkedList <String> ();
					lastWhitespace = true;
					isLeft = true;
					inList = false;
				}else if(c == listStart){
					throw new IOException("DBList begin character found in the middle of a list");
				}else if(c == '='){
					throw new IOException("DBList contains an '=' character");
				}else{
					//(default)
					term.append(c);
					lastWhitespace = false;
				}
			}else{
				//--Standard Value
				if(c == escapeChar){
					//(escaped character)
					i = handleEscape(file, i, term);
					lastWhitespace = false;
				}else if(inLiteral){
					//(we are in a single-quoted string)
					if(c == '\''){
						inLiteral = false;
					}else{
						term.append(c);
					}
				}else if(c == '\''){
					inLiteral = true;
				}else if(c == '\n'){
					//(newline stops a value)
					register(left.toString(), term.toString().trim());
					left = new StringBuilder();
					term = new StringBuilder();
					lastWhitespace = true;
					isLeft = true;
					inList = false;
				}else if(isWhitespace(c)){
					//(append a single space for whitespace)
					if(!lastWhitespace){
						term.append(' ');
					}
					lastWhitespace = true;
				}else if(c == listStart){
					//(start of a list)
					if(term.length() != 0){
						throw new IOException("DBList begin character found in the middle of a non-list term: " + term.toString());
					}else{
						if(!lastWhitespace){ throw new IllegalStateException("Unexpected lastWhitepsace"); }	//internal
						inList = true;
					}
				}else if(c == listEnd){
					throw new IOException("DBList stop character found in the middle of a term");
				}else if(c == '='){
					throw new IOException("Multiple '=' characters in a line");
				}else{
					term.append(c);
					lastWhitespace = false;
				}
			}
			//note: dangerous to put code here
		}
	}

	public Object remove(String key){
		Object o = elements.remove(key.toLowerCase());
		if(o == null){ throw new IllegalArgumentException("No such key in config file: " + key); }
		return o;
	}

	public boolean contains(String key){
		return elements.containsKey(key.toLowerCase());
	}
	
	public Object get(String key, Object defVal){
		Object o = elements.get(key.toLowerCase());
		if(o == null){ 
			if(defVal == null) throw new IllegalArgumentException("No such key in config file: " + key);
			else return defVal;
		}
		return o;
	}
	
	public String getString(String key){ return getString(key, null); }
	public String getString(String key, String defVal){
		Object o = get(key, defVal);
		if(o instanceof String){
			return (String) o;
		}else{
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a String");
		}
	}
	
	public int getInt(String key){ 
		String str = get(key, null).toString();
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Value associated with '" + key + "' is not an Integer");
		}
	}
	public int getInt(String key, int defVal){
		String str = get(key, "" + defVal).toString();
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Value associated with '" + key + "' is not an Integer");
		}
	}
	
	public double getDouble(String key){
		String str = get(key, null).toString();
		try {
			return Double.parseDouble(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a Double");
		}
	}
	public double getDouble(String key, double defVal){
		String str = get(key, "" + defVal).toString();
		try {
			return Double.parseDouble(str);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a Double");
		}
	}

	public boolean getBoolean(String key){
		String str = get(key, null).toString();
		if(str.equalsIgnoreCase("false")){
			return false;
		}else if(str.equalsIgnoreCase("true")){
			return true;
		}else{
			throw new IllegalArgumentException("Value associated with key '" + key + "' is not a boolean: " + str);
		}
	}

	public boolean getBoolean(String key, boolean defVal){
		String str = get(key, "false").toString();
		if(str.equalsIgnoreCase("false")){
			return false;
		}else if(str.equalsIgnoreCase("true")){
			return true;
		}else{
			throw new IllegalArgumentException("Value associated with key '" + key + "' is not a boolean: " + str);
		}
	}
	
	@SuppressWarnings("unchecked")
	public List <Object> getList(String key){
		Object o = get(key, null);
		if(o instanceof List){
			return (List <Object>) o;
		}else{
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a DBList");
		}
	}
	
	@SuppressWarnings("unchecked")
	public List <String> getStringList(String key){
		Object o = get(key, null);
		if(o instanceof List){
			List <String> rtn = new LinkedList <String> ();
			for(Object elem : ((List <Object>) o)){
				rtn.add(elem.toString());
			}
			return rtn;
		}else{
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a DBList");
		}
	}
	
	@SuppressWarnings("unchecked")
	public List <Integer> getIntList(String key){
		Object o = get(key, null);
		if(o instanceof List){
			List <Integer> rtn = new LinkedList <Integer> ();
			for(Object elem : ((List <Object>) o)){
				try {
					rtn.add(new Integer(Integer.parseInt(elem.toString())));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("DBList associated with key '" + key + "' has non-integer term '" + elem + "'");
				}
			}
			return rtn;
		}else{
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a DBList");
		}
	}
	
	@SuppressWarnings("unchecked")
	public List <Double> getDoubleList(String key){
		Object o = get(key, null);
		if(o instanceof List){
			List <Double> rtn = new LinkedList <Double> ();
			for(Object elem : ((List <Object>) o)){
				try {
					rtn.add(new Double(Double.parseDouble(elem.toString())));
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("DBList associated with key '" + key + "' has non-double term '" + elem + "'");
				}
			}
			return rtn;
		}else{
			throw new IllegalArgumentException("Value associated with '" + key + "' is not a DBList");
		}
	}

	/**
	* Exports the config file as a string of command line
	* options; The command line format is: <br/>
	* -key value <br/>
	* DBList values are represented as -key a,b,c,d.
	*
	* @return A string array of command line options
	*/
	@SuppressWarnings("unchecked")
	public String[] toOptions(){
		List<String> opts = new LinkedList<String>();
		for(String name : elements.keySet()){
			opts.add("-" + name);
			Object val = elements.get(name);
			if(val instanceof List){
				//(append a list)
				if(((List) val).size() > 0){
					StringBuilder b = new StringBuilder();
					for(Object term : ((List) val)){
						b.append(term).append(",");
					}
					opts.add( b.substring(0, b.length() - 1 ) );
				}else{
					opts.add("null");
				}
			} else {
				//(else append a value)
				opts.add(val.toString());
			}
		}
		return opts.toArray(new String[opts.size()]);
	}
	
	@SuppressWarnings("unchecked")
	public String toString(){
		StringBuilder b = new StringBuilder();
		for(String name : elements.keySet()){
			b.append(name).append(" = ");
			Object val = elements.get(name);
			if(val instanceof List){
				b.append("[ ");
				if(((List) val).size() > 0){
					for(Object term : ((List) val)){
						b.append(term).append(", ");
					}
					b = new StringBuilder( b.substring(0, b.length() - 2) ).append(" ");
				}
				b.append("]");
			}else{
				b.append(val);
			}
			b.append("\n");
		}
		return b.toString();
	}
	
	private static final int handleEscape(char[] file, int i, StringBuilder toAppend) throws IOException{
		if(i == file.length-1){ throw new IOException("Escape character ends config file"); }
		char c = file[i+1];
		i = i+1;
		toAppend.append(c);
		return i;
	}
	
	private static final boolean isWhitespace(char c){
		return c == ' ' || c == '\n' || c == '\t' || c == '\r';
	}
	
	/*
	public static void main(String[] args){
		ConfigFile f = new ConfigFile("/home/gabor/test.conf");
		try {
			f.read();
			System.out.println(f);
			f.getString("String");
			f.getInt("int");
			f.getDouble("double");
			f.getList("list");
			f.getIntList("intlist");
			f.getDoubleList("doublelist");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	*/
}
