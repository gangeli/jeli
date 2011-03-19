package org.goobs.exec;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.goobs.database.Database;
import org.goobs.io.LazyFileIterator;
import org.goobs.testing.DBResultLogger;
import org.goobs.testing.Dataset;
import org.goobs.testing.DatasetDB;
import org.goobs.testing.Datum;
import org.goobs.testing.InMemoryLogger;
import org.goobs.testing.ResultLogger;
import org.goobs.utils.Marker;
import org.goobs.utils.Utils;

/*
 *	TODO casting to native arrays (in Utils.cast)
 *	TODO better options logging to file
*/

public final class Execution {	
	
	private static final String LOG_TAG = "EXEC";
	
	private static final String SCALA_PATH = "scalaPath";
	private static final String[] IGNORED_JARS = {
		"junit.jar",
		"scala-library.jar",
		"scala-compiler.jar",
	};
	private static final Class[] BOOTSTRAP_CLASSES = {
		Execution.class,
		Log.class,
	};

	@Option(name="ignoreClasspath", 
		gloss="Do not try to load options from anything matching these")
	private static String[] ignoredClasspath = new String[0];
	

	@Option(name="execName", gloss="Assigns a name for this particular run")
	private static String runName = "<unnamed>";
	@Option(name="execOutput",gloss="Database to store parameters and results in")
	private static Database outputDB;
	@Option(name="execData", gloss="Location of dataset(s)")
	private static Database dataDB;
	@Option(name="execDir", gloss="Directory to log stuff to")
	protected static String execDir;
	
	private static ResultLogger logger;
	
	/*
	 * ----------
	 * OPTIONS
	 * ----------
	 */
	
	private static final Map<String,String> parseOptions(String[] args){
		Map <String, String> opts = new HashMap<String,String>();
		String key = null;
		String value = null;
		for(String term : args){
			if(term.startsWith("-")){
				//(case: a key)
				while(term.startsWith("-")){
					term = term.substring(1);
				}
				if(key != null){
					//must be a boolean
					opts.put(key, "true");
				}
				key = term;
				value = null;
			}else{
				//(case: a value)
				if(value != null) throw new IllegalArgumentException("Invalid options sequence: " + (key==null ? "" : "-" + key) + " " + value + " " + term);
				opts.put(key, term);
				key = null;
				value = term;
			}
		}
		if(key != null){
			opts.put(key, "true");
		}
		return opts;
	}
	
	private static final void fillField(Field f, String value){
		try {
			//--Permissions
			boolean accessState = true;
			if(Modifier.isFinal( f.getModifiers() )){
				Log.err(LOG_TAG,"Option cannot be final: " + f);
				System.exit(ExitCode.BAD_OPTION.code);
			}
			if(!f.isAccessible()){
				accessState = false;
				f.setAccessible(true);
			}
			//--Set Value
			Object objVal = Utils.cast(value, f.getGenericType());
			if(objVal != null){
				if(objVal.getClass().isArray()){
					//(case: array)
					Object[] array = (Object[]) objVal;
					// error check
					if(!f.getType().isArray()){
						Log.err(LOG_TAG,"Setting an array to a non-array field. field: " + f + " value: " + Arrays.toString(array) + " src: " + value);
						System.exit(ExitCode.BAD_OPTION.code);
					}
					// create specific array
					Object toSet = Array.newInstance(f.getType().getComponentType(), array.length);
					for(int i=0; i<array.length; i++){
						Array.set(toSet, i, array[i]);
					}
					// set value
					f.set(null, toSet);
				} else {
					//case: not array
					f.set(null, objVal);
				}
			} else {
				Log.err(LOG_TAG,"Cannot assign option field: " + f + " value: " + value + "; invalid type");
				System.exit(ExitCode.BAD_OPTION.code);
			}
			//--Permissions
			if(!accessState){
				f.setAccessible(false);
			}
		} catch (IllegalArgumentException e) {
			Log.err(LOG_TAG,"Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName() + " value: " + value + " cause: " + e.getMessage());
			System.exit(ExitCode.BAD_OPTION.code);
		} catch (IllegalAccessException e) {
			Log.err(LOG_TAG,"Cannot access option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName());
			System.exit(ExitCode.BAD_OPTION.code);
		} catch (Exception e){
			Log.err(LOG_TAG,"Cannot assign option field: " + f.getDeclaringClass().getCanonicalName() + "." + f.getName() + " value: " + value + " cause: " + e.getMessage());
			System.exit(ExitCode.BAD_OPTION.code);
		}
	}

	@SuppressWarnings("rawtypes")
	private static final Class filePathToClass(String cpEntry, String path) {
		if (path.length() <= cpEntry.length()){
			throw new IllegalArgumentException("Illegal path: cp=" + cpEntry
					+ " path=" + path);
		}
		if (path.charAt(cpEntry.length()) != '/'){
			throw new IllegalArgumentException("Illegal path: cp=" + cpEntry
					+ " path=" + path);
		}
		path = path.substring(cpEntry.length() + 1);
		path = path.replaceAll("/", ".").substring(0, path.length() - 6);
		try {
			return Class.forName(path, 
					false, 
					ClassLoader.getSystemClassLoader());
		} catch (ClassNotFoundException e) {
			throw Log.internal("Could not load class at path: " + path);
		} catch (NoClassDefFoundError ex) {
			Log.debug(LOG_TAG,"Class at path " + path + " is unloadable");
			return null;
		}
	}
	
	private static final boolean isIgnored(String path){
		for(String ignore : IGNORED_JARS){
			if(path.endsWith(ignore)){
				return true;
			}
		}
		return false;
	}
	
	private static final void ensureScalaPath(Map<String,String> options, String[] cp){
		//(check if it's in the classpath)
		try {
			Class.forName("scala.None", false, ClassLoader.getSystemClassLoader());
		} catch (ClassNotFoundException e) {
			//(case: scala library not in the classpath)
			if(options.containsKey(SCALA_PATH)){
				//(case: scala_path option set)
				try {
					String path = options.get(SCALA_PATH);
					if(!(new File(path).exists())){
						System.err.println("The library strongly integrates with the Scala runtime, ");
						System.err.println("however it could not find the Scala library (scala-library.jar) ");
						System.err.println("at the path given by the command line option '" + SCALA_PATH + "': " + path);
						Log.exit(ExitCode.BAD_OPTION);
					}
					URL url = new File(path).toURI().toURL();
					URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
					Class <URLClassLoader> sysclass = URLClassLoader.class;
					Method method = sysclass.getDeclaredMethod("addURL", url.getClass());
					boolean savedAccessible = method.isAccessible();
					method.setAccessible(true);
					method.invoke(sysloader, new Object[]{url});
					method.setAccessible(savedAccessible);
					Class.forName("scala.None",false, ClassLoader.getSystemClassLoader());
				} catch (Exception ex) {
					throw Log.fail(ex);
				}//end try catch
			}else{
				//(case: we cannot find the scala library at all)
				System.err.println("The library strongly integrates with the Scala runtime, ");
				System.err.println("however it could not find the Scala library (scala-library.jar) in the classpath, ");
				System.err.println("and the '" + SCALA_PATH + "' command line argument is not set.");
				Log.exit(ExitCode.BAD_OPTION);
			}
		}
		options.remove(SCALA_PATH);
	}
	
	private static final Class<?>[] getVisibleClasses(Map<String,String> options){
		//--Variables
		List<Class<?>> classes = new ArrayList<Class<?>>();
		// (get classpath)
		String pathSep = System.getProperty("path.separator");
		String[] cp = System.getProperties().getProperty("java.class.path",
				null).split(pathSep);
		// --Configuration
		ensureScalaPath(options, cp);
		// --Fill Options
		// (get classes)
		for (String entry : cp) {
			//(should skip?)
			if(entry.equals(".") || entry.trim().length() == 0){
				continue;
			}
			boolean isIgnored = false;
			for(String pattern : ignoredClasspath){
				if(entry.matches(pattern)){ 
					Log.debug(LOG_TAG,"Ignoring options in classpath element: " + entry);
					isIgnored = true;
					break;
				}
			}
			if(isIgnored){ continue; }
			//(no, don't skip)
			File f = new File(entry);
			if (f.isDirectory()) {
				// --Case: Files
				LazyFileIterator iter = new LazyFileIterator(f, ".*class$");
				while (iter.hasNext()) {
					//(get the associated class)
					Class <?> clazz = filePathToClass(entry, iter.next().getPath());
					if(clazz != null){
						//(add the class if it's valid)
						classes.add(clazz);
					}
				}
			} else if(!isIgnored(entry)){
				// --Case: Jar
				try {
					JarFile jar = new JarFile(f);
					Enumeration<JarEntry> e = jar.entries();
					while (e.hasMoreElements()) {
						//(for each jar file element)
						JarEntry jarEntry = e.nextElement();
						String clazz = jarEntry.getName();
						if (clazz.matches(".*class$")) {
							//(if it's a class)
							clazz = clazz.substring(0, clazz.length() - 6)
							.replaceAll("/", ".");
							//(add it)
							try {
								classes.add(
										Class.forName(clazz, 
												false, 
												ClassLoader.getSystemClassLoader()));
							} catch (ClassNotFoundException ex) {
								throw Log
								.internal("Could not load class in jar: "
										+ f + " at path: " + clazz);
							} catch (NoClassDefFoundError ex) {
								Log.debug(LOG_TAG,"Could not scan class: " + clazz + " (in jar: " + f + ")");
							}
						}
					}
				} catch (IOException e) {
					throw Log.internal("Could not open jar file: " + f +
						"(are you sure the file exists?)");
				}
			} else {
				//case: ignored jar
			}
		}
		
		return classes.toArray(new Class<?>[classes.size()]);
	}

	protected static final Map<String,Field> fillOptions(
			Class<?>[] classes, 
			Map<String,String> options ){
		return fillOptions(classes, options, true);
	}

	@SuppressWarnings("rawtypes")
	protected static final Map<String,Field> fillOptions(
			Class<?>[] classes, 
			Map<String,String> options, 
			boolean ensureAllOptions  ) {

		//--Get Fillable Options
		Map<String, Field> canFill = new HashMap<String,Field>();
		Map<String,Marker> required = new HashMap<String,Marker>();
		Map<String, String> interner = new HashMap<String,String>();
		for(Class c : classes){
			Field[] fields = null;
			try {
				fields = c.getDeclaredFields();
			} catch (Throwable e) {
				Log.debug(LOG_TAG,"Could not check fields for class: " + c.getName() + "  (caused by " + e.getClass() + ": " + e.getMessage() + ")");
				continue;
			}
			
			for(Field f : fields){
				Option o = f.getAnnotation(Option.class);
				if(o != null){
					//(check if field is static)
					if((f.getModifiers() & Modifier.STATIC) == 0){
						Log.err(LOG_TAG,"Option can only be applied to static field: " + c + "." + f);
						System.exit(ExitCode.BAD_OPTION.code);
					}
					//(required marker)
					Marker mark = null;
					if(o.required()){
						mark = new Marker();
						mark.unset();
					}
					//(add main name)
					String name = o.name().toLowerCase();
					if(name.equals("")){ name = f.getName().toLowerCase(); }
					if(canFill.containsKey(name)){
						String name1 = canFill.get(name).getDeclaringClass().getCanonicalName() + "." + canFill.get(name).getName();
						String name2 = f.getDeclaringClass().getCanonicalName() + "." + f.getName();
						if(!name1.equals(name2)){
							Log.err(LOG_TAG,"Multiple declarations of option " + name + ": " + name1 + " and " + name2);
							System.exit(ExitCode.BAD_OPTION.code);
						}else{
							Log.warn(LOG_TAG,"Class is in classpath multiple times: " + canFill.get(name).getDeclaringClass().getCanonicalName());
						}
					}
					canFill.put(name, f);
					if(mark != null) required.put(name, mark);
					interner.put(name, name);
					//(add alternate names)
					if(!o.alt().equals("")){
						for(String alt : o.alt().split(" *, *")){
							alt = alt.toLowerCase();
							if(canFill.containsKey(alt) && !alt.equals(name))
								throw new IllegalArgumentException("Multiple declarations of option " + alt + ": " + canFill.get(alt) + " and " + f);
							canFill.put(alt, f);
							if(mark != null) required.put(alt, mark);
							interner.put(alt, name);
						}
					}
				}
			}
		}
		
		//--Fill Options
		for(String key : options.keySet()){
			String rawKey = key;
			key = key.toLowerCase();
			// (get values)
			String value = options.get(rawKey);
			Field target = canFill.get(key);
			// (mark required option as fulfilled)
			Marker mark = required.get(key);
			if(mark != null){ mark.set(); }
			// (fill the field)
			if(target != null){
				// (case: declared option)
				fillField(target, value);
			}else if(ensureAllOptions){
				// (case: undeclared option)
				// split the key
				int lastDotIndex = rawKey.lastIndexOf('.');
				if(lastDotIndex < 0){
					Log.err(LOG_TAG,"Unrecognized option: " + key);
					System.exit(ExitCode.BAD_OPTION.code);
				}
				String className = rawKey.substring(0, lastDotIndex);
				String fieldName = rawKey.substring(lastDotIndex + 1);
				// get the class
				Class clazz = null;
				try {
					clazz = ClassLoader.getSystemClassLoader().loadClass(className);
				} catch (Exception e) {
					Log.err(LOG_TAG,"Could not set option: " + rawKey + "; no such class: " + className);
					System.exit(ExitCode.BAD_OPTION.code);
				}
				// get the field
				try {
					target = clazz.getField(fieldName);
				} catch (Exception e) {
					Log.err(LOG_TAG,"Could not set option: " + rawKey + "; no such field: " + fieldName + " in class: " + className);
					System.exit(ExitCode.BAD_OPTION.code);
				}
				fillField(target, value);
			}
		}
		
		//--Ensure Required
		boolean good = true;
		for(String key : required.keySet()){
			if(!required.get(key).isSet()){
				Log.err(LOG_TAG,"Missing required option: " + interner.get(key) + "   <in class: " + canFill.get(key).getDeclaringClass() + ">");
				required.get(key).set();	//don't duplicate error messages
				good = false;
			}
		}
		if(!good){ System.exit(ExitCode.BAD_OPTION.code); }
		
		return canFill;
	}
	
	/*
	 * ----------
	 * DATABASE
	 * ----------
	 */
	
	protected static final void initDatabase(Class<?>[] classes, Map<String,String> options, Map<String,Field> optionFields){
		if(outputDB == null){ return; }
		//--Init Database
		outputDB.connect();
		DBResultLogger logger = new DBResultLogger(outputDB, runName);
		Execution.logger = logger;
		outputDB.beginTransaction();
		//--Add Options
		for(String key : optionFields.keySet()){
			Field f = optionFields.get(key.toLowerCase());
			//(try to save the declared option)
			String value = options.get(key);
			if(value == null){
				//(if no declared option, get field value)
				try {
					boolean accessSave = true;
					if(!f.isAccessible()){ accessSave = false; f.setAccessible(true); } 
					Object v = f.get(null);
					if(v == null){
						value = "<null>";
					}else if(v.getClass().isArray()){
						value = Arrays.toString((Object[]) v);
					}else{
						value = v.toString();
					}
					if(!accessSave){ f.setAccessible(false); }
				} catch (IllegalArgumentException e) {
					Log.fail(e);
				} catch (IllegalAccessException e) {
					Log.fail(e);
				}
			}
			logger.logOption(key, value, f.getDeclaringClass().getName() + "." + f.getName());
		}
		//--Add Parameters
		for(Class<?> c : classes){
			//(get fields for class)
			Field[] fields = null;
			try {
				fields = c.getDeclaredFields();
			} catch (Throwable e) {
				Log.debug(LOG_TAG,"Could not check fields for class: " + c.getName() + "  (caused by " + e.getClass() + ": " + e.getMessage() + ")");
				continue;
			}
			//(get parameters for fields)
			for(Field f : fields){
				Param p = f.getAnnotation(Param.class);
				if(p != null){
					if( (f.getModifiers() & Modifier.STATIC) == 0){
						throw Log.fail("Cannot set a non-static field as an input parameter: " + f);
					}
					//(get name)
					String name = null;
					if(!p.name().equals("")){
						name = p.name();
					}else if(f.getAnnotation(Option.class) != null){
						name = f.getAnnotation(Option.class).name();
					}else{
						name = f.getName();
					}
					//(save)
					try {
						logger.logParameter(name, "" + f.get(null));
					} catch (IllegalArgumentException e) {
						throw Log.fail(e);
					} catch (IllegalAccessException e) {
						throw Log.fail(e);
					}
				}
			}
		}
		//--Commit
		outputDB.endTranaction();
	}
	
	public static final void setOutputDatabase(Database d){
		outputDB = d;
	}
	
	public static final void setDataDatabase(Database d){
		dataDB = d;
	}
	public static final Database getDataDatabase(){
		return dataDB;
	}
	
	/*
	 * ----------
	 * LOGGING
	 * ----------
	 */
	
	public static final ResultLogger getLogger(){
		if(logger == null){ 
			Log.warn(LOG_TAG, "In-memory logging only (log database options were not set?)");
			logger = new InMemoryLogger();
		}
		return logger;
	}
	
	public static final <D extends Datum> Dataset<D> getDataset(Class<D> type){
		return getDataset(type,false);
	}
	public static final <D extends Datum> Dataset<D> getDataset(Class<D> type, boolean lazy){
		return new DatasetDB<D>(dataDB, type, lazy);
	}

	private static String execDirFull = null;
	public static final File touch(String relativePath) throws IOException{
		//--Ensure Directory
		if(execDirFull == null && execDir != null){
			//(get next index)
			int id = 0; //keep 0 so it increments to 1
			if(logger != null && logger instanceof DBResultLogger){
				id = ((DBResultLogger) logger).runIndex();
			}else{
				//(ensure base dir)
				File base = new File(execDir);
				if(!base.exists()){
					base.mkdirs();
				}
				//(get highest index)
				Pattern numRegex = Pattern.compile("\\d+");
				String[] ls = base.list();
				for(String f : ls){
					Matcher m = numRegex.matcher(f);
					if(m.find()){
						int cand = Integer.parseInt(m.group());
						if(cand > id){
							id = cand;
						}
					}
				}
				//(increment it by one)
				id += 1;
			}
			//(create directory)
			execDirFull = execDir + "/" + id + ".exec";
			(new File(execDirFull)).mkdirs();
		}
		if(execDirFull == null){ return null; }
		//--Create File
		//(create subdirectories)
		int lastSlash = relativePath.lastIndexOf('/');
		if(lastSlash >= 0){
			(new File(
				execDirFull + "/" + relativePath.substring(0,lastSlash)
				)).mkdirs();
		}
		//(create file)
		File rtn = new File(execDirFull + "/" + relativePath);
		rtn.createNewFile();
		return rtn;
	}

	private static final void dumpOptions(Map<String,String> options){
		StringBuilder b = new StringBuilder();
		for(String key : options.keySet()){
			b.append("--").append(key).append(" \"")
				.append(options.get(key)).append("\" \\\n");
		}
		try{
			File f = touch("options");
			if(f != null){
				FileWriter w = new FileWriter(f);
				w.write(b.toString());
				w.close();
			}
		} catch(IOException e){	Log.warn("Could not write options file"); }
	}

	/*
	 * ----------
	 * EXECUTION
	 * ----------
	 */
	
	public static final void exec(Runnable toRun, String[] args) {
		exec(toRun, args, true);
	}
	public static final void exec(Runnable toRun, String[] args, boolean exit) {
		//--Init
		Log.start_track("init");
		//(bootstrap)
		Map<String,String> options = parseOptions(args); //get options
		fillOptions(BOOTSTRAP_CLASSES, options, false); //bootstrap
		//(fill options)
		Class<?>[] visibleClasses = getVisibleClasses(options); //get classes
		Map<String,Field> optionFields = fillOptions(visibleClasses, options);//fill
		initDatabase(visibleClasses, options, optionFields); //database
		dumpOptions(options); //file dump
		Log.end_track();
		//--Run Program
		try {
			Log.startTrack("main");
			toRun.run();
			Log.endTrack(); //ends main
			Log.startTrack("flushing");
			if(logger != null){ logger.save(); }
		} catch (Exception e) { //catch everything
			e.printStackTrace();
			System.err.flush();
			if(logger != null){ logger.suggestFlush(); } //not a save!
			Log.exit(ExitCode.FATAL_EXCEPTION);
		}
		Log.endTrack();
		if(exit){
			Log.exit(ExitCode.OK,false); //soft exit
		}
	}
	
	public static final void exec(Runnable toRun){
		exec(toRun, new String[0]);
	}

	private static final String threadRootClass() {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		StackTraceElement elem = trace[trace.length - 1];
		String clazz = elem.getClassName();
		return clazz;
	}

	public static final void usageAndExit(String[] expectedArgs) {
		String clazz = threadRootClass();
		StringBuilder b = new StringBuilder();
		b.append("USAGE: ").append(clazz).append(" ");
		for (String arg : expectedArgs) {
			b.append(arg).append(" ");
		}
		System.out.println(b.toString());
		System.exit(ExitCode.INVALID_ARGUMENTS.code);
	}

	public static final void usageAndExit(Map<String, String[]> argToFlagsMap) {
		String clazz = threadRootClass();
		StringBuilder b = new StringBuilder();
		b.append("USAGE: ").append(clazz).append("\n\t");
		for (String arg : argToFlagsMap.keySet()) {
			String[] flags = argToFlagsMap.get(arg);
			if (flags == null || flags.length == 0) {
				throw new IllegalArgumentException(
						"No flags registered for arg: " + arg);
			}
			b.append("{");
			for (int i = 0; i < flags.length - 1; i++) {
				b.append(flags[i]).append(",");
			}
			b.append(flags[flags.length - 1]).append("}");
		}
		System.out.println(b.toString());
		System.exit(ExitCode.INVALID_ARGUMENTS.code);
	}
	
	
}
