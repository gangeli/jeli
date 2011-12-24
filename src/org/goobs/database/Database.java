package org.goobs.database;

import org.goobs.io.Console;
import org.goobs.io.TextConsole;
import org.goobs.utils.Decodable;
import org.goobs.utils.MetaClass;
import org.goobs.utils.Pair;
import org.goobs.utils.Utils;

import java.io.File;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
TODO
  - Unique flag
  - Fix the hack in ResultSetIterator
 */
public final class Database implements Decodable{
	
	public static boolean FORCEDBUPDATE = false;
	
	private static final Pattern PATTERN_CONNINFO 
		= Pattern.compile(" *([a-zA-Z][a-zA-Z0-9]*)@([a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*):([a-zA-Z0-9]+)(<.+)? *");
	
	protected static final class DBClassInfo<E>{
		protected MetaClass.ClassFactory <E> factory;
		private Field primaryKey;
		private PreparedStatement onCreate, onUpdate;
		private Map<Field,PreparedStatement> keySearch;
		private Map<Field,PreparedStatement> keyDelete;
		private Field[] fields;
		private int primaryKeyIndex = -1;

		private DBClassInfo(
				MetaClass.ClassFactory <E> factory, 
				Field primaryKey,
				PreparedStatement onCreate, 
				PreparedStatement onUpdate,
				Map<Field,PreparedStatement> keySearch,
				Map<Field,PreparedStatement> keyDelete,
				Field[] fields){
			//--Set Variables
			this.factory    = factory;
			this.primaryKey = primaryKey;
			this.onCreate   = onCreate;
			this.onUpdate   = onUpdate;
			this.keySearch  = keySearch;
			this.keyDelete  = keyDelete;
			this.fields     = fields;
			//--Set Computations
			if(primaryKey != null){
				int i=0;
				for(Field f : fields){
					if(f.getAnnotation(PrimaryKey.class) != null){ primaryKeyIndex = i; }
					i += 1;
				}
			}
		}
	}
	
	
	private static final int MYSQL	=	1;
	private static final int SQLITE	=	2;
	private static final int PSQL	=	3;
	
	private static final String POSTGRES_DRIVER = "org.postgresql.Driver"; //Postgres driver
	private static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver"; // MySQL MM JDBC driver
	private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
	
	private int type;
	private String server, username, password, schema;
	private File sqlite;
	private Connection conn = null;
	private Statement lastStatement;
	private boolean inTransaction = false;

	private Map<Class,Map<Pair<Field,Object>,WeakReference<Object>>> internerMap;
	
	private final class ResultSetIterator<E extends DatabaseObject> implements Iterator<E>{
		private ResultSet rs;
		private MetaClass.ClassFactory<E> fact;
		private DBClassInfo<E> info;
		private Class<E> classType;
		private E next = null;
		private boolean done = false;
		
		private ResultSetIterator(ResultSet rs, Class<E> classType){
			this.rs = rs;
			this.classType = classType;
			this.info = ensureClassInfo(classType);
			this.fact = new MetaClass(classType).createFactory(new Class[0]);
			if(type == MYSQL){
				try {
					rs.beforeFirst();
				} catch (SQLException e) {
					throw new DatabaseException(e);
				}
			}
		}
		@Override
		public boolean hasNext() {
			try {
        if(done){ return false; }
				if(next == null){
					if(rs.next()){
						//(create a class)
						E obj = null;
						if(info.primaryKey != null){
							obj = mkObject(classType,info.primaryKey,castResult(rs, info.primaryKeyIndex, info.primaryKey.getType()),fact);
						} else {
							obj = fact.createInstance();
						}
						if(!obj.isInDatabase()){
							obj.init(Database.this, classType, null, DatabaseObject.FLAG_IN_DB);
						}
						populateObject(info, rs, obj);
						next = obj;
						return true;
					}else{
						rs.close();
						done = true;
						return false;
					}
				}else{
					return true;
				}
			} catch (SQLException e) {
        if(e.getMessage().equals("This ResultSet is closed.")){ done = true; return false; } //TODO strange hack with Postgres on getObjectsByKey()?
				throw new DatabaseException(e);
			}
		}
		@Override
		public E next() {
			if(next == null && !hasNext()){
				throw new NoSuchElementException();
			}else{
				E term = next;
				next = null;
				return term;
			}
		}
		@Override
		public void remove() {
			throw new NoSuchMethodError();
		}
	}
	
	public static final class ConnInfo{
		private String fileName;
		private File file;
		private String server, username, password, schema;
		private int type;
		//--Constructors
		private ConnInfo(){}
		public static ConnInfo mysql(String server, String username, String password, String schema){
			ConnInfo rtn = new ConnInfo();
			rtn.server = server;
			rtn.username = username;
			rtn.password = password;
			rtn.schema = schema;
			rtn.type = MYSQL;
			return rtn;
		}
		public static ConnInfo psql(String server, String username, String password, String schema){
			ConnInfo rtn = new ConnInfo();
			rtn.server = server;
			rtn.username = username;
			rtn.password = password;
			rtn.schema = schema;
			rtn.type = PSQL;
			return rtn;
		}
		public static ConnInfo sqlite(String file){
			ConnInfo rtn = new ConnInfo();
			rtn.fileName = file;
			rtn.type = SQLITE;
			return rtn;
		}
		public static ConnInfo sqlite(File file){
			ConnInfo rtn = new ConnInfo();
			rtn.file = file;
			rtn.type = SQLITE;
			return rtn;
		}
		//--Validate
		private void validate(){
			switch(type){
			case SQLITE:
				if(file == null){
					if(fileName == null){ throw new DatabaseException("Cannot create sqlite DB without a file"); }
					file = new File(fileName);
				}
				if(!file.exists()){
					throw new DatabaseException("File for sqlite DB must exist");
				}
				break;
			case PSQL:
			case MYSQL:
				if(server == null || username == null || password == null || schema == null){
					throw new DatabaseException("MySql and Postgresql need all of: server, username, password, schema");
				}
			}
		}
	}
	
	public static final class IndexInfo{
		private String name;
		private String table;
		private String[] fields;
		private Index.Type type;
		public String getName(){ return name; }
		public String getTable(){ return table; }
		public String[] getFields(){ return fields; }
		public Index.Type getType(){ return type; }
	}
	
	public boolean isLite(){
		return type == SQLITE;
	}

	protected Database(){}
	
	public Database(ConnInfo info){
		info.validate();
		this.type = info.type;
		this.sqlite = info.file;
		this.server = info.server;
		this.username = info.username;
		this.password = info.password;
		this.schema = info.schema;
	}

	/*
	 * BASIC FUNCTIONALITY
	 */
	
	public boolean isConnected(){
		return conn != null;
	}
	
	/**
	 * Connect to a database, as specified in the constructor.
	 * This can either be a MYSQL or SQLITE database.
	 * @return True if the connection was successful, else False.
	 */
	public Database connect() {
		internerMap = new HashMap<Class,Map<Pair<Field,Object>,WeakReference<Object>>>();
		try {
			String url = null;
			switch(type){
			case MYSQL:
				//(MySQL)
				Class.forName(MYSQL_DRIVER);	//initalize
				url = "jdbc:mysql://" + server + "/" + schema; // a JDBC url
				conn = DriverManager.getConnection(url, username, password);
				break;
			case PSQL:
				//(MySQL)
				Class.forName(POSTGRES_DRIVER);	//initalize
				url = "jdbc:postgresql://" + server + "/" + schema; // a JDBC url
				conn = DriverManager.getConnection(url, username, password);
				break;
			case SQLITE:
				//(SqLite)
				System.setProperty("sqlite.purejava", "true");
				Class.forName(SQLITE_DRIVER);
				conn = DriverManager.getConnection("jdbc:sqlite:" + sqlite.getAbsolutePath());
				break;
			default:
				throw new DatabaseException("Invalid database type: " + type);
			}
			conn.setAutoCommit(true);
		} catch (ClassNotFoundException e) {
			throw new DatabaseException("Must include class in classpath to use database: " + e.getMessage());
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
		return this;
	}

	public void disconnect(){
		internerMap = null;
		DatabaseObject.clearInfo(this);
		try {
			conn.close();
			conn = null;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public void beginTransaction(){
		try {
			if(inTransaction) throw new DatabaseException("Already in a transaction");
			conn.setAutoCommit(false);
			inTransaction = true;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public void endTransaction(){
		try {
			if(!inTransaction) throw new DatabaseException("Ending a non-existent transaction");
			conn.commit();
			conn.setAutoCommit(true);
			inTransaction = false;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	public void commit(){
		try {
			conn.commit();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void clear(){
		for(String table : getTableNames()){
			if(!(type == SQLITE && table.equalsIgnoreCase("SQLITE_SEQUENCE"))){
				update("DROP TABLE " + table + (type == SQLITE ? "" : " CASCADE;"));
			}
		}
	}

	
	private String[] getTableNames(ResultSet resultSet) throws SQLException{
		// Get the table names
		ArrayList<String> rtnLst = new ArrayList<String>();
		while (resultSet.next()) {
			// Get the table name
			rtnLst.add(resultSet.getString(3));
		}
		String[] rtn = new String[rtnLst.size()];
		for (int i = 0; i < rtnLst.size(); i++) {
			rtn[i] = rtnLst.get(i);
		}
		return rtn;
	}
	
	/**
	 * Get the names of all the tables in the schema.
	 * @return An array of all the table names, or null if an error was encountered.
	 */
	public String[] getTableNames() {
		if (conn == null) {
			throw new DatabaseException("Database has not been connected yet");
		}
		ensureConnection();
		switch(type){
		case PSQL:
			try {
				// Gets the database metadata
				DatabaseMetaData dbmd = conn.getMetaData();
				// Specify the type of object; in this case we want tables
				ResultSet resultSet = dbmd.getTables(null, null, "%", new String[]{"TABLE"});
				//Return table names
				return getTableNames(resultSet);
			} catch (SQLException e) {
				throw new DatabaseException(e);
			}
		case MYSQL:
			try {
				// Gets the database metadata
				DatabaseMetaData dbmd = conn.getMetaData();
				// Specify the type of object; in this case we want tables
				ResultSet resultSet = dbmd.getTables(null, schema, "%", new String[]{"TABLE"});
				//Return table names
				return getTableNames(resultSet);
			} catch (SQLException e) {
				throw new DatabaseException(e);
			}
		case SQLITE:
			try {
				String query = "SELECT tbl_name FROM sqlite_master;";
				if(conn == null) throw new DatabaseException("Querying without an open database connection");
				ensureConnection();
				Statement stmt = conn.createStatement();
				stmt.execute(query);
				ResultSet results = stmt.getResultSet();
				LinkedList <String> lst = new LinkedList<String>();
				while(results.next()){
					lst.add(results.getString(1));
				}
				stmt.close();
				return lst.toArray(new String[lst.size()]);
			} catch (SQLException e) {
				throw new DatabaseException(e);
			}
		default:
			throw new DatabaseException("Cannot get tables for database type: " + type);
		}
	}

	public static String getTableName(Class<? extends DatabaseObject> clazz){
		if( MetaClass.findAnnotation(clazz, Table.class) == null ){
			throw new IllegalStateException("Database object has no Table annotation");
		} else {
			return MetaClass.findAnnotation(clazz, Table.class).name();
		}
	}

	/**
	 * Returns whether the given table exists.
	 * Note that the table name is case insensitive.
	 * @param table The table to check for
	 * @return True if the table exists in the schema
	 */
	public boolean hasTable(String table) {
		for(String s : getTableNames()){
			if(s.equalsIgnoreCase(table)) return true;
		}
		return false;
	}
	
	public <E extends DatabaseObject> boolean hasTable(Class<E> clazz){
		try {
			return hasTable(MetaClass.findAnnotation(clazz, Table.class).name());
		} catch (RuntimeException e) {
			if(e instanceof DatabaseException){ throw e; }
			throw new DatabaseException("Could not find table with class: " + clazz);
		}
	}
	
	/**
	 * Returns a downcased list of each of the table columns.
	 * Note: the downcasing is for psql behavior consistency.
	 * @param table The table to get the fields for
	 * @return A lowercase array of fields for that table
	 */
	public String[] getTableColumns(String table){
		switch(this.type){
		case MYSQL:
		case SQLITE:
			break;
		case PSQL:
			table = table.toLowerCase();
			break;
		default:
			throw new DatabaseException("[Internal]: unknown database type: " + type);
		}
		
		try {
			ResultSet rsColumns = null;
			DatabaseMetaData meta = conn.getMetaData();
			rsColumns = meta.getColumns(null, null, table, null);
			LinkedList <String> columns = new LinkedList<String>();
			while (rsColumns.next()) {
				columns.add(rsColumns.getString("COLUMN_NAME").toLowerCase());
			}
			return columns.toArray(new String[columns.size()]);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Drops a table from the database.
	 * @param clazz The class corresponding to the table to be dropped
	 * @param <E> The type of the database object being dropped
	 * @return true if the table was in the database
	 */
	public <E extends DatabaseObject> boolean dropTable(Class<E> clazz){
		if(!this.hasTable(clazz)){ return false; }
		Table ann = MetaClass.findAnnotation(clazz, Table.class);
		if(ann == null){ throw new IllegalArgumentException("Class " + clazz + " has no !Table annotation"); }
		update("DROP TABLE " + ann.name() + " CASCADE;");
		return true;
	}
	
	private <T extends DatabaseObject> Pair<String,String> class2pk(Class<T> table){
		//(variables)
		String name = MetaClass.findAnnotation(table, Table.class).name();
		String key = null;
		boolean foundPK = false;
		//(find a key)
		for(Field f : MetaClass.getDeclaredFields(table)){
			for(Annotation ann : f.getAnnotations()){
				if(ann instanceof PrimaryKey){
					key = ((PrimaryKey) ann).name();
					foundPK = true;
				}
				if(ann instanceof Key && key == null){
					key = ((Key) ann).name();
				}
			}
		}
		//(error check)
		if(key == null){ throw new DatabaseException("Could not find a valid key to count on!"); }
		if(!foundPK){
			System.err.println("WARNING: Could not find primary key to count rows on. Using " + key + " instead");
		}
		return Pair.make(name, key);
	}
	
	public <T extends DatabaseObject> int getTableRowCount(Class<T> table){
		Pair<String,String> info = class2pk(table);
		return getTableRowCount(info.car(), info.cdr());
	}
	
	public int getTableRowCount(String table, String key){
		String query = "SELECT COUNT(" + key + ") FROM " + table +";";
		ResultSet rs = query(query);
		try {
			if(!rs.next()){
				throw new DatabaseException("Could not get row count!");
			}
			int rtn = rs.getInt(1);
			rs.close();
			return rtn;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public <T extends DatabaseObject> int max(Class<T> table){
		Pair<String,String> info = class2pk(table);
		return max(info.car(), info.cdr());
	}
	
	public int max(String table, String key){
		String query = "SELECT " + key + " FROM " + table +" ORDER BY " + key + " DESC LIMIT 1;";
		ResultSet rs = query(query);
		try {
			if(!rs.next()){
				throw new DatabaseException("Could not get row count!");
			}
			int rtn = rs.getInt(1);
			rs.close();
			return rtn;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public <T extends DatabaseObject> int min(Class<T> table){
		Pair<String,String> info = class2pk(table);
		return min(info.car(), info.cdr());
	}
	
	public int min(String table, String key){
		String query = "SELECT " + key + " FROM " + table +" ORDER BY " + key + " ASC LIMIT 1;";
		ResultSet rs = query(query);
		try {
			if(!rs.next()){
				throw new DatabaseException("Could not get row count!");
			}
			int rtn = rs.getInt(1);
			rs.close();
			return rtn;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	public IndexInfo[] getTableIndices(String table){
		throw new DatabaseException("TODO not yet implemented");
	}

	/*
	 * OBJECT MAPPING
	 */

	private String indexQuery(Index.Type type, String table, String fields, int uniqueID){
		StringBuilder b = new StringBuilder();
		//--Begin Statement
		if(type == Index.Type.RTREE){
			b.append("CREATE SPATIAL INDEX ");
			throw new DatabaseException("Yeah, RTREE isn't really implemented. I don't think I even know what it does really. Sorry.");
		}else{
			b.append("CREATE INDEX ");
		}
		//--Create Index
		b.append(table.toLowerCase()).append("_").append(fields.replaceAll(",","_")).append("_").append(uniqueID);
		if(this.type == MYSQL){
			b.append(" USING ").append(type.name())
				.append(" ON ").append(table).append("(").append(fields).append(");");
		}else if(this.type == SQLITE){
			b.append(" ON ").append(table).append("(").append(fields).append(");");
		}else{ //psql syntax
			b.append(" ON ").append(table)
				.append(" USING ").append(type.name())
				.append(" (").append(fields).append(");");
		}
		return b.toString();
	}

	private String indexQuery(Annotation index, Field f, String table, int uniqueID){
		Index.Type type;
		String fields;
		//--Get fields
		if(index instanceof CompoundIndex){
			type = ((CompoundIndex) index).type();
			StringBuilder b = new StringBuilder();
			boolean shouldComma = false;
			for(String s : ((CompoundIndex) index).fields()){
				if(shouldComma) b.append(",");
				b.append(s);
				shouldComma = true;
			}
			fields = b.toString();
		}else if(index instanceof Index){
			type = ((Index) index).type();
			//(get key)
			Key key = f.getAnnotation(Key.class);
			//(error checks)
			if(key == null) throw new DatabaseException("An index can only be created on a normal key: " + f);
			if((nonNative(f.getType()) && !Class.class.isAssignableFrom(f.getType()) && Serializable.class.isAssignableFrom(f.getType()))){
				if(key.length() <= 0) throw new DatabaseException("An indexed key of type Serializable must have a [positive] length defined: " + f);
			}
			//(save fields)
			fields = key.name();
		}else if(index instanceof Parent){
			type = ((Parent) index).indexType();
			fields = ((Parent) index).localField();
		}else{
			throw new IllegalArgumentException("Called indexQuery() with non-index annotation");
		}
		//--Create Query
		return indexQuery(type,table,fields,uniqueID);
	}
	
	/**
	 * Create a table if that table does not exist. If the table exists,
	 * then do nothing.
	 * @param toCreate the class of the table to ensure is in the database
	 * @return returns itself
	 */
	@SuppressWarnings({"unchecked"})
	public <E extends DatabaseObject> boolean ensureTable(Class<E> toCreate){
		//--Variables
		//(keys and indices)
		String table = MetaClass.findAnnotation(toCreate, Table.class).name();
		LinkedList<Field> foreignKeys = new LinkedList<Field>();
		LinkedList<String> indexes = new LinkedList <String> ();
		//(query building)
		StringBuilder query = new StringBuilder();
		query.append("CREATE TABLE ").append(table).append("(");
		boolean printComma = false;
		//(state variables)
		int numKeys = 0;
		boolean seenPrimaryKey = false;
		int nextIndexIdentifier = 0;
		
		//--Check Table
		if(hasTable(MetaClass.findAnnotation(toCreate, Table.class).name())){
			return true;
		}		
		
		//--For Each Field...
		for(Field f : MetaClass.getDeclaredFields(toCreate)){
			boolean entered = false;
			//--Add It's Annotation
			for(Annotation ann : f.getAnnotations()){
				String fieldName = null;
				if(ann instanceof PrimaryKey){
					if(entered) throw new DatabaseException("Multiple keys for field: " + f + " in class " + toCreate);
					if(printComma) query.append(",");
					if(seenPrimaryKey){ throw new DatabaseException("Duplicate primary key: " + f + " in class " + toCreate); }
					seenPrimaryKey = true;
					fieldName = ((PrimaryKey) ann).name();
					query.append("\n\t");
					query.append( fieldName ).append(" ");
					if( !typeJava2sql(type,f, -1).equalsIgnoreCase("INTEGER") ){ throw new DatabaseException("Primary key must be an integer"); }
					if(type == SQLITE){
						query.append(" INTEGER PRIMARY KEY");	//I hate sqlite
					}else{
						query.append(" SERIAL PRIMARY KEY");
					}
					entered = true;
				}else if(ann instanceof Parent){
					if(entered) throw new DatabaseException("Multiple keys for field: " + f + " in class " + toCreate);
					if(printComma) query.append(",");
					fieldName = ((Parent) ann).localField();
					query.append("\n\t");
					query.append( fieldName ).append(" ");
					query.append(typeJava2sql(type,f, -1));
					foreignKeys.add(f);
					entered = true;
					if(((Parent) ann).indexType() != Index.Type.NONE){
						Parent pann = (Parent) ann;
						indexes.add(indexQuery(pann.indexType(), table, pann.localField(), nextIndexIdentifier++));
					}
				}else if(ann instanceof Key){
					if(entered) throw new DatabaseException("Multiple keys for field: " + f + " in class " + toCreate);
					if(printComma) query.append(",");
					fieldName = ((Key) ann).name();
					query.append("\n\t");
					query.append( fieldName ).append(" ");
					query.append(typeJava2sql(type,f, ((Key) ann).length(), ((Key) ann).type()));
					//(r-tree index requires not null)
					if(f.getAnnotation(Index.class) != null 
							&& f.getAnnotation(Index.class).type() == Index.Type.RTREE){
						query.append(" NOT NULL");
					}
					entered = true;
				}else if(ann instanceof Index){
					if(((Index) ann).type() != Index.Type.NONE){ indexes.add(indexQuery(ann, f, table,nextIndexIdentifier++)); }
				}else if(ann instanceof IndexList){
					for(Index i : ((IndexList) ann).value()){
						if(i.type() != Index.Type.NONE){ indexes.add(indexQuery(i,f,table,nextIndexIdentifier++)); }
					}
				}
				if(entered){ numKeys += 1; printComma = true; }
				//--Ensure Field Name Validity
				if(fieldName != null){
					if(fieldName.trim().contains(" ")){
						throw new DatabaseException("Field names cannot contain spaces: " + fieldName);
					}
				}
			}
		}
		query.append("\n);");
		//(error check)
		if(numKeys == 0 || (numKeys == 1 && seenPrimaryKey)){
			throw new DatabaseException("Cannot create an empty table, or table with only a primary key");
		}
		
		//--Create Table
		update(query.toString());
		
		//--Foreign Keys
		if(type != SQLITE){
			for(Field f : foreignKeys){
				//(variables)
				Parent key = f.getAnnotation(Parent.class);
				Table foreignTable = f.getType().isArray() ?
						MetaClass.findAnnotation(f.getType().getComponentType(), Table.class)
						: f.getType().getAnnotation(Table.class);
				if(foreignTable == null){ 
					throw new DatabaseException("Target type of foreign key has no @Table annotation: " + f.getType());
				}
				StringBuilder fkQuery = new StringBuilder();
				String local = key.localField();
				String target = key.parentField();
				//(build query)
				ensureTable((Class<? extends DatabaseObject>) f.getType());
				fkQuery.append("ALTER TABLE ").append(table).append(" ADD FOREIGN KEY (")
				.append(local).append(") REFERENCES ")
				.append(foreignTable.name())
				.append("(").append(target).append(");");
				update(fkQuery.toString());
			}
		}
		
		//--Indexes
		//(single field)
		for(String indexQuery : indexes){
			update(indexQuery);
		}
		//(compound)
		for(Annotation ann : toCreate.getAnnotations()){
			if(ann instanceof CompoundIndex){
				update(indexQuery(ann, null, table, nextIndexIdentifier++));
			}else if(ann instanceof CompoundIndexList){
				for(CompoundIndex i : ((CompoundIndexList) ann).value()){
					update(indexQuery(i, null, table, nextIndexIdentifier++));
				}
			}
		}
		return true;
	}

	@SuppressWarnings({"unchecked"})
	public <E extends DatabaseObject> E emptyObject(Class<E> classType, Object... args){
		E rtn = (E) new MetaClass(classType).createInstance(args);
		rtn.init(this, classType, args, DatabaseObject.FLAG_NONE);
		return rtn;
	}

	private void cacheObject(Object instance, Field field, Object fieldValue){
		Class<?> clazz = instance.getClass();
		Pair<Field, Object> key = Pair.make(field, fieldValue);
		Map<Pair<Field, Object>, WeakReference<Object>> interner = internerMap.get(clazz);
		//(get map)
		if(interner == null){
			interner = new HashMap<Pair<Field, Object>, WeakReference<Object>>();
			internerMap.put(clazz,interner);
		}
		//(get reference)
		WeakReference<Object> ref = interner.get(key);
		if(ref == null || ref.get() == null){
			ref = new WeakReference<Object>(instance);
			interner.put(key,ref);
		} else {
			throw new IllegalArgumentException("Caching an object which is already in the cache: " + instance);
		}
	}

	@SuppressWarnings({"unchecked"})
	private synchronized <E extends DatabaseObject> E mkObject(Class<E> classType, Field f, Object value, MetaClass.ClassFactory fact){
		Map<Pair<Field, Object>, WeakReference<Object>> interner = internerMap.get(classType);
		if(interner == null){
			interner = new HashMap<Pair<Field, Object>, WeakReference<Object>>();
			internerMap.put(classType,interner);
		}

		Pair<Field,Object> key = Pair.make(f, value);
		WeakReference<Object> rtn = interner.get(key);
		Object toSet = null;
		if(rtn != null){ toSet = rtn.get(); }
		if(toSet == null){
			toSet = fact.createInstance();
			if(rtn != null && rtn.get() == null){ interner.remove(key); }
			rtn = new WeakReference<Object>(toSet);
			interner.put(key, rtn);
		}
		return (E) toSet;

	}

	@SuppressWarnings({"unchecked"})
	private <E extends DatabaseObject> E cachedObject(Class<E> classType, ResultSet rs) throws SQLException{
		DBClassInfo<E> info = ensureClassInfo(classType);
		E rtn = null;
		//--Get Object
		if(info.primaryKey == null){
			//(case: no primary key; can't cache)
			rtn = (E) new MetaClass(classType).createInstance();
		} else {
			//(case: object might be in the cache)
			Object pk = castResult(rs, info.primaryKeyIndex, info.primaryKey.getType());
			rtn = mkObject(classType, info.primaryKey, pk, new MetaClass(classType).createFactory());
		}
		//--Process Object
		if(rtn != null && !rtn.isInDatabase()){
			populateObject(info, rs, rtn);
			rtn.init(this, classType, null, DatabaseObject.FLAG_IN_DB);
		}
		//--Return
		return rtn;
	}
	
	public <E extends DatabaseObject> void registerType(Class<E> classType, Class...args){
		DBClassInfo<E> info = DatabaseObject.getInfo(classType, this);
		if(info != null){
			DatabaseObject.register(this, classType, createObjectInfo(classType, args));
		}
	}

	public <E extends DatabaseObject> E registerObject(E obj, Object...args){
		obj.init(this, obj.getClass(), args, DatabaseObject.FLAG_NONE);
		return obj;
	}

	public <E extends DatabaseObject> int deleteObjectsWhere(Class<E> classType, String whereClause) {
    if(!this.hasTable(classType)){ return 0; }
		//--Prepare query
		StringBuilder query = new StringBuilder();
		Table table = MetaClass.findAnnotation(classType, Table.class);
		if(table == null){ throw new DatabaseException("Class extends database object but does not define @table annotation: " + classType); }
		query.append("DELETE FROM ").append(table.name());
		if(whereClause != null && !whereClause.trim().equals("")){ 
			query.append(" WHERE ").append(whereClause); 
		}
		query.append(";");
		//--Query
		return update(query.toString());
    }

    public <E extends DatabaseObject> boolean deleteObjectById(Class<E> clazz, int id){
		try {
			DBClassInfo<E> info = ensureClassInfo(clazz);
			if(info.primaryKey == null){ throw new DatabaseException("Cannot delete object by id: object has no primary key: " + clazz); }
			PreparedStatement psmt = info.keyDelete.get(info.primaryKey);
			psmt.setInt(1, id);
			int updated = psmt.executeUpdate();
			return updated == 1;
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public <E extends DatabaseObject> E getFirstObject(Class<E> classType, String query){		
		//--Query
		ResultSet results = query(query);
		try {
			//--Get Result
			if(!results.next()){
				return null;
			}
			//(get the result)
			return cachedObject(classType, results);
		} catch (SQLException e) {
			return null;
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <E extends DatabaseObject> Iterator <E> getObjects(Class<E> classType, String query){
		ResultSet results = query(query);
		lastStatement = null; //don't auto-clean
		return new ResultSetIterator(results, classType);
	}
	
	public <E extends DatabaseObject> E getFirstObjectWhere(Class<E> classType, String whereClause){
		//--Prepare query
		StringBuilder query = new StringBuilder();
		Table table = MetaClass.findAnnotation(classType, Table.class);
		if(table == null){ throw new DatabaseException("Class extends database object but does not define @table annotation: " + classType); }
		query.append("SELECT * FROM ").append(table.name());
		if(whereClause != null && !whereClause.trim().equals("")){ 
			query.append(" WHERE ").append(whereClause); 
		}
		query.append(";");
		//--Query
		ResultSet results = query(query.toString());
		try {
			//--Get Result
			if(!results.next()){
				return null;
			}
			//(get the result)
			return cachedObject(classType, results);
		} catch (SQLException e) {
			return null;
		}
	}
	
	public <E extends DatabaseObject> Iterator <E> getObjectsWhere(Class<E> classType, String whereClause){
		//--Prepare query
		StringBuilder query = new StringBuilder();
		Table table = MetaClass.findAnnotation(classType, Table.class);
		if(table == null){ throw new DatabaseException("Class extends database object but does not define @table annotation: " + classType); }
		query.append("SELECT * FROM ").append(table.name());
		if(whereClause != null && !whereClause.trim().equals("")){ 
			query.append(" WHERE ").append(whereClause); 
		}
		query.append(";");
		//--Query
		ResultSet results = query(query.toString());
		lastStatement = null; //don't auto-clean
		return new ResultSetIterator<E>(results, classType);
	}
	
	
	public <E extends DatabaseObject> E getObjectById(Class<E> clazz, int id){
		try {
			DBClassInfo<E> info = ensureClassInfo(clazz);
			if(info.primaryKey == null){ throw new DatabaseException("Cannot get object by id: object has no primary key: " + clazz); }
			PreparedStatement psmt = info.keySearch.get(info.primaryKey);
			psmt.setInt(1, id);
			ResultSet results = psmt.executeQuery();
			if(!results.next()){
				return null;
			}
			return cachedObject(clazz,results);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public <E extends DatabaseObject> E getObjectByKey(Class<E> clazz, String key, Object value){
		try {
			DBClassInfo<E> info = ensureClassInfo(clazz);
			PreparedStatement psmt = null;
			//(get key)
			for(Field x : info.keySearch.keySet()){
				if(x.getAnnotation(Key.class) != null){
					if(x.getAnnotation(Key.class).name().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}else if(x.getAnnotation(PrimaryKey.class) != null){
					if(x.getAnnotation(PrimaryKey.class).name().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}else if(x.getAnnotation(Parent.class) != null){
					if(x.getAnnotation(Parent.class).localField().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}
			}
			//(error check)
			if(psmt == null){ 
				throw new DatabaseException("No such indexed key: " + key + "; in class: " + clazz); 
			}
			//(run query)
			if(value instanceof Class){ value = ((Class) value).getName(); }
			psmt.setObject(1, value);
			psmt.execute();
			ResultSet results = psmt.getResultSet();
			if(!results.next()){
				return null;
			}
			return cachedObject(clazz, results);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	public <E extends DatabaseObject> Iterator<E> getObjectsByKey(Class<E> clazz, String key, Object value){
		try {
			DBClassInfo<E> info = ensureClassInfo(clazz);
			PreparedStatement psmt = null;
			//(get key)
			for(Field x : info.keySearch.keySet()){
				if(x.getAnnotation(Key.class) != null){
					if(x.getAnnotation(Key.class).name().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}else if(x.getAnnotation(PrimaryKey.class) != null){
					if(x.getAnnotation(PrimaryKey.class).name().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}else if(x.getAnnotation(Parent.class) != null){
					if(x.getAnnotation(Parent.class).localField().equalsIgnoreCase(key)){
						psmt = info.keySearch.get(x);
					}
				}
			}
			//(error check)
			if(psmt == null){ 
				throw new DatabaseException("No such indexed key: " + key + "; in class: " + clazz); 
			}
			//(get object)
			psmt.setObject(1, value);
			psmt.execute();
			ResultSet results = psmt.getResultSet();
			return new ResultSetIterator<E>(results,clazz);			
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	
	protected <F extends DatabaseObject> DBClassInfo<F>  createObjectInfo(Class<F> clazz, Class<?>[] constructorParams){
		//--Overhead
		//(query)
		StringBuilder onCreate = new StringBuilder();
		StringBuilder onUpdate = new StringBuilder();
		//(variables)
		HashMap<String,Field> keys = new HashMap <String,Field> ();
		List<String> foreignKeys = new LinkedList<String>();
		Field primaryKey = null; PrimaryKey pKey = null;
		HashMap <Field, PreparedStatement> findByIndex = new HashMap <Field,PreparedStatement>();
        HashMap <Field, PreparedStatement> delByIndex = new HashMap <Field,PreparedStatement>();
		HashSet <Field> indexedTerms = new HashSet <Field>();
		
		try{
			//--The Fields
			for(Field f : MetaClass.getDeclaredFields(clazz)){
				boolean hasIndex = false;;
				boolean hasKey = false;
				//(for each annotation)
				for(Annotation ann : f.getAnnotations()){
					//(primary key)
					if(ann.annotationType() == PrimaryKey.class){
						if(pKey != null){
							throw new DatabaseException("Multiple primary keys: " + primaryKey + " and " + f);
						}
						pKey = (PrimaryKey) ann;
						keys.put(pKey.name(), f);
						primaryKey = f;
						hasKey = true;
						hasIndex = true;	//primary key always indexed
					}
					//(key)
					if(ann.annotationType() == Key.class){
						Key key = (Key) ann;
						keys.put(key.name(), f);
						hasKey = true;
					}
					//(foreign key)
					if(ann.annotationType() == Parent.class){
						Parent key = (Parent) ann;
						keys.put(key.localField(), f);
						foreignKeys.add(key.localField());
						hasKey = true;
						hasIndex = true;
					}
					//(index)
					if(ann.annotationType() == Index.class){
						hasIndex = true;
					}
					if(ann.annotationType() == IndexList.class){
						if(((IndexList) ann).value().length > 0){
							hasIndex = true;
						}
					}
				}
				//(prepare index)
				if(hasIndex){
					if(!hasKey){ throw new DatabaseException("Index defined on a field without a key defined: " + f); }
					indexedTerms.add(f);
				}
				
			}
			
			//--Get/Create Table
			if(MetaClass.findAnnotation(clazz,Table.class) == null){ throw new IllegalStateException("Database has no @Table annotation: " + clazz); }
			String table = MetaClass.findAnnotation(clazz,Table.class).name();
			if(!hasTable(table) && !ensureTable(clazz)){ throw new DatabaseException("Could not create table " + table); }
			
			//--Sort Fields
			//(variables)
			String[] names = new String[keys.size()];
			Field[] fields = new Field[keys.size()];
			String[] registeredFields = getTableColumns(table);
			if(registeredFields.length != names.length){
				return recoverClassChanged(clazz,constructorParams,keys);
			}
			//(keys)
			int i=0;
			for(String str : keys.keySet()){
				int index = Utils.indexOf(registeredFields, str.toLowerCase());
				if(index < 0){ throw new DatabaseException("Key " + str + " in class " + clazz + " does not exist in the database"); }
				if(names[index] != null){ throw new DatabaseException("Duplicate definition of key: " + str + " in class: " + clazz); }
				names[index] = str;
				fields[index] = keys.get(str);
			}
			
			//--Create Indices
			for(Field key : indexedTerms){
                //(find query)
				StringBuilder q = new StringBuilder();
				q.append("SELECT * FROM ").append(table).append(" WHERE ")
					.append(field2name(key)).append("=?");
				findByIndex.put(key, conn.prepareStatement(q.toString()));
                //(delete query)
                StringBuilder d = new StringBuilder();
                d.append("DELETE FROM ").append(table).append(" WHERE ")
                    .append(field2name(key)).append("=?");
                delByIndex.put(key, conn.prepareStatement(d.toString()));
			}
			
			//--On Create Query
			onCreate.append("INSERT INTO ").append(table).append(" (");
			//(keys)
			boolean first = true;
			for(i=0; i<names.length; i++){
				if(pKey == null || !names[i].equals(pKey.name())){
					if(!first) onCreate.append(", ");
					onCreate.append(names[i]);	
					first = false;
				}
			}
			onCreate.append(") VALUES (");
			//(values)
			for(i=0; i<(pKey==null ? fields.length : fields.length-1); i++){
				if(i > 0) onCreate.append(", ");
				onCreate.append("?");
			}
			onCreate.append(");");
			
			//--On Update Query
			onUpdate.append("UPDATE ").append(table).append(" SET ");
			for(i=0; i<names.length; i++){
				if(i > 0) onUpdate.append(", ");
				onUpdate.append(names[i]).append("=?");
			}
			if(pKey != null){
				onUpdate.append(" WHERE ").append(pKey.name()).append("=?");
			}
			onUpdate.append(";");
	
			//--Return
			PreparedStatement stmtCreate = null;
			if(type == SQLITE){
				stmtCreate = conn.prepareStatement(onCreate.toString());
			}else{
				stmtCreate = conn.prepareStatement(onCreate.toString(), Statement.RETURN_GENERATED_KEYS);
			}
			PreparedStatement stmtUpdate = conn.prepareStatement(onUpdate.toString());
			MetaClass.ClassFactory<F> factory = MetaClass.create(clazz).createFactory(constructorParams);
			return new DBClassInfo<F>(factory, primaryKey, stmtCreate, stmtUpdate, findByIndex, delByIndex, fields);
		} catch (Exception e) {
			if(e instanceof DatabaseException){
				throw (DatabaseException) e;
			}else{
				throw new DatabaseException(e);
			}
		}
	}

	protected <F extends DatabaseObject> DBClassInfo<F>  recoverClassChanged(
				Class<F> clazz,
				Class<?>[] constructorParams,
				HashMap<String,Field> keys){
		//--Display warning
		System.err.println("WARNING: Class " + clazz.getName() + " is out of sync with the database.");
		if(!FORCEDBUPDATE){
			Console c = new TextConsole().show();
			if(!c.readBoolean("Allow automatic update (y/N)? ")){
				throw new DatabaseException("Class definition is out of sync with the database: " + clazz);
			}
		}
		System.err.println("Attempting to recover");
		
		//TODO implement me
		throw new NoSuchMethodError("Not implemented yet");
	}
	
	private void ensureConnection(){
		if (conn != null) {
			try {
				if(conn.getMetaData() == null){
					//(case: no metadata returned)
					conn = null;
					connect();
				}
			} catch (SQLException e) {
				//(case: conn exception'd)
				conn = null;
				connect();
			}
		} else {
			//(case; never pretended to be connected)
			connect();
		}
	}
	
	@SuppressWarnings("unchecked")
	private <E extends DatabaseObject> void addRow(DBClassInfo<E> info, E instance){
		int slot = 1;
		for(int i=0; i<info.fields.length; i++){
			try {
				Field f = info.fields[i];
				Class ftype = f.getType();
				if(f.getAnnotation(Key.class) != null && f.getAnnotation(Key.class).type() != Object.class){
					ftype = f.getAnnotation(Key.class).type();
				}
				boolean restore = true;
				if(!f.isAccessible()){ f.setAccessible(true); restore = false; }
				if(f.getAnnotation(Parent.class) != null){
					//(case: foreign key)
					E foreign = (E) f.get(instance);
					if(foreign == null){
						info.onCreate.setObject(slot, null);
					}else{
						String pkey = foreign.getInfo().primaryKey.getAnnotation(PrimaryKey.class).name();
						if(pkey == null){ throw new DatabaseException("Referenced object has no primary key: " + f.getType()); }
						int primaryKey = -1;
						try {
							Field fld = MetaClass.findField(foreign.getClass(), pkey);
							boolean savePerm = fld.isAccessible();
							if(!savePerm) fld.setAccessible(true);
							primaryKey = fld.getInt(foreign);
							if(!savePerm) fld.setAccessible(savePerm);
						} catch (SecurityException e) {
							if(!restore) f.setAccessible(false);
							throw new DatabaseException(e);
						} catch (NoSuchFieldException e) {
							if(!restore) f.setAccessible(false);
							throw new DatabaseException(e);
						}
						info.onCreate.setObject(slot, primaryKey);
					}
				}else if(Decodable.class.isAssignableFrom(f.getType())){
					Object o = f.get(instance);
					if(o == null){ info.onCreate.setString(slot, null); }
					else { info.onCreate.setString(slot, ((Decodable) f.get(instance)).encode()); }
				} else if(nonNative(ftype) && !Class.class.isAssignableFrom(ftype) && Serializable.class.isAssignableFrom(ftype)){
					//(case: non-native serializable)
					if(type == SQLITE) throw new DatabaseException("Cannot write serializable objects to sqlite database (try Decodable instead?)");
					info.onCreate.setBytes(slot, Utils.obj2bytes((Serializable) f.get(instance)));
				}else{
					//(case: native)
					if(f != info.primaryKey) obj2db(info.onCreate, slot, f.get(instance));
				}
				//(overhead)
				if(!restore) f.setAccessible(false);
				if(f != info.primaryKey){ slot += 1; }
			} catch (IllegalArgumentException e) {
				throw new DatabaseException(e);
			} catch (SQLException e) {
				throw new DatabaseException(e);
			} catch (IllegalAccessException e) {
				throw new DatabaseException("Trying to save a final field: " + info.fields[i]);
			}
		}
		try {
			//(execute)
			info.onCreate.execute();
			//(set primary key)
			if (info.onCreate.getUpdateCount() == 1 && info.primaryKey != null) {
				ResultSet res = info.onCreate.getGeneratedKeys();
				if(res == null || !res.next()){ throw new DatabaseException("Could not get created row (res.next() call failed)"); }
				int id = -1;
				if(type == PSQL){
					id = res.getInt(info.primaryKey.getAnnotation(PrimaryKey.class).name());
				}else{
					id = res.getInt(1);
				}
				boolean saveAccessible = true;
				if(!info.primaryKey.isAccessible()){ saveAccessible = false; info.primaryKey.setAccessible(true); }
				info.primaryKey.setInt(instance, id);
				if(!saveAccessible){ info.primaryKey.setAccessible(false); }
			}
		} catch (SQLException e) {
			throw new DatabaseException(e);
		} catch (IllegalArgumentException e) {
			throw new DatabaseException(e);
		} catch (SecurityException e) {
			throw new DatabaseException(e);
		} catch (IllegalAccessException e) {
			throw new DatabaseException(e);
		}
	}

	private <E extends DatabaseObject> void updateRow(DBClassInfo<E> info, E instance){
		//(set fields)
		for(int i=0; i<info.fields.length; i++){
			try {
				Field f = info.fields[i];
				Class<?> ftype = f.getType();
				if(f.getAnnotation(Key.class) != null && f.getAnnotation(Key.class).type() != Object.class){
					ftype = f.getAnnotation(Key.class).type();
				}
				boolean restore = true;
				if(!f.isAccessible()){ f.setAccessible(true); restore = false; }
				if(f.getAnnotation(Parent.class) != null){
					//(foreign key handled specially)
					Parent fkey = f.getAnnotation(Parent.class);
					Object target = f.get(instance);
					if(target == null){
						info.onUpdate.setNull(i+1, java.sql.Types.INTEGER);
					}else{
						Field toFillFrom;
						try {
              toFillFrom = MetaClass.findField(f.getType(), fkey.parentField());
						} catch (SecurityException e) {
							throw new DatabaseException(e);
						} catch (NoSuchFieldException e) {
							throw new DatabaseException("Foreign key references non-existent field (" + f + "): " + fkey.parentField());
						}
						boolean accessible = toFillFrom.isAccessible();
						if(!accessible){ toFillFrom.setAccessible(true); }
						info.onUpdate.setInt(i+1, toFillFrom.getInt(target));
						if(!accessible){ toFillFrom.setAccessible(false); }
					}
				}else if(nonNative(ftype) && ftype != Class.class && ftype instanceof Serializable){
					if(type == SQLITE) throw new DatabaseException("Cannot write serializable objects to sqlite database (try Decodable?)");
					info.onUpdate.setBytes(i+1, Utils.obj2bytes((Serializable) f.get(instance)));
				}else{
					obj2db(info.onUpdate, i+1, f.get(instance));
				}
				if(!restore) f.setAccessible(false);
			} catch (IllegalArgumentException e) {
				throw new DatabaseException(e);
			} catch (SQLException e) {
				throw new DatabaseException(e);
			} catch (IllegalAccessException e) {
				throw new DatabaseException("Trying to save a final field: " + info.fields[i]);
			}
		}
		//(set primary key cond)
		try {
			boolean restore = true;
			if(!info.primaryKey.isAccessible()){ info.primaryKey.setAccessible(true); restore = false; }
			int key = info.primaryKey.getInt(instance);
			if(!restore) info.primaryKey.setAccessible(false);
			info.onUpdate.setInt(info.fields.length+1, key);
		} catch (IllegalArgumentException e1) {
			throw new DatabaseException(e1);
		} catch (IllegalAccessException e1) {
			throw new DatabaseException(e1);
		} catch (SQLException e1) {
			throw new DatabaseException(e1);
		}
		//(flush)
		try {
			info.onUpdate.execute();
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	/**
	 * Flush this object to the database (i.e. eventually to disk).
	 * This method will create a new object if it does not exist
	 * @param <E> The instance of the object being flushed
	 * @param <F> The class of the object being flushed; same as E in practice
	 * @param info The info for the class of the object being flushed
	 * @param instance The object being flushed
	 * @return The primary key of the object flushed
	 */
	protected <E extends DatabaseObject, F extends E> void flush(DBClassInfo<E> info, E instance){
		if(instance.isInDatabase()){
			updateRow(info, instance);
		}else{
			addRow(info, instance);
			instance.setInDatabase(true);
			if(info.primaryKey != null){
				try {
					boolean restore = false;
					if(!info.primaryKey.isAccessible()){ restore = true; info.primaryKey.setAccessible(true); }
					cacheObject(instance, info.primaryKey, info.primaryKey.get(instance));
					if(restore){ info.primaryKey.setAccessible(false);}
				} catch (IllegalAccessException e) {
					throw new DatabaseException("Could not cache object: " + instance);
				}
			}
		}
	}


    private void prepareStatement() throws SQLException{
      if(conn == null) throw new DatabaseException("Querying without an open database connection");
			ensureConnection();
	    if(lastStatement != null){ lastStatement.close(); }
	    lastStatement = conn.createStatement();
    }
	/**
	 * Run a raw database query. The raw result of this query
	 * can be retrieved in getLastStatementResult()
	 * NOTE: remember to close the statement after a query
	 * @param query The raw query to run, in SQL syntax
	 * @return The result of the query
	 */
	private ResultSet query(String query) {
		try {
            prepareStatement();
            return lastStatement.executeQuery(query);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

    /**
	 * Run a raw database update.
	 * @param query The raw query to run, in SQL syntax
	 * @return The number of updated rows
	 */
	private int update(String query) {
		try {
			prepareStatement();
			return lastStatement.executeUpdate(query);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
	
	private static void obj2db(PreparedStatement stmt, int slot, Object obj) throws SQLException{
		if(obj instanceof Boolean){ //boolean
			stmt.setBoolean(slot, (boolean) ((Boolean) obj));
		}else if(obj instanceof Byte){								//Integer Fields
			stmt.setShort(slot, (short) (Byte) obj);
		}else if(obj instanceof Character){
			stmt.setShort(slot, (short) ((Character) obj).charValue());
		}else if(obj instanceof Short){
			stmt.setShort(slot, (Short) obj);
		}else if(obj instanceof Integer){
			stmt.setInt(slot, (Integer) obj);
		}else if(obj instanceof Long){
			stmt.setLong(slot, (Long) obj);
		}else if(obj instanceof Float){							//Floating Point Fields
			float f = (Float) obj;
			if(Float.isInfinite(f) || Float.isNaN(f)){
				stmt.setObject(slot, null);
			}else{
				stmt.setFloat(slot, f);
			}
		}else if(obj instanceof Double){
			double d = (Double) obj;
			if(Double.isInfinite(d) || Double.isNaN(d)){
				stmt.setObject(slot, null);
			}else{
				stmt.setDouble(slot, d);
			}
		}else if(obj instanceof String){						//String
			stmt.setString(slot, (String) obj);
		}else if(obj instanceof Decodable){
			stmt.setString(slot, ((Decodable) obj).encode());
		}else if(obj != null && isEnum(obj.getClass())){
			stmt.setString(slot, obj.toString());
		}else if(obj instanceof Date){
			stmt.setObject(slot, new java.sql.Timestamp(((Date) obj).getTime()));
		}else if(obj instanceof Calendar){
			stmt.setObject(slot, new java.sql.Timestamp(((Calendar) obj).getTimeInMillis()));
		} else if(obj instanceof Class){
			stmt.setString(slot, ((Class) obj).getName());
		}else if(obj != null && obj.getClass().isArray()){
			stmt.setString(slot, Arrays.toString((Object[]) obj));
		}else{
			stmt.setObject(slot, obj);
		}
	}

	private static Object db2obj(Class<?> type, Object o){
		if(o == null){
			if(type == boolean.class){
					return false;
			} else if(type == float.class || type == Float.class){
				return Float.NaN;
			} else if(type == double.class || type == Double.class){
				return Double.NaN;
			} else {
				return null;
			}
		}else if(type == long.class || type == Long.class){				//long
			return ((Number) o).longValue();
		}else if(type == int.class || type == Integer.class){		//int
			return ((Number) o).intValue();
		}else if(type == short.class || type == Short.class){		//short
			return ((Number) o).shortValue();
		}else if(type == char.class || type == Character.class){	//char
			return (char) ((Number) o).intValue();
		}else if(type == byte.class || type == Byte.class){			//byte
			return ((Number) o).byteValue();
		}else if(type == boolean.class || type == Boolean.class){			//boolean
			return ((Boolean) o).booleanValue();
		}else if(type == double.class || type == Double.class){		//double
			return ((Number) o).doubleValue();
		}else if(type == float.class || type == Float.class){		//float
			return ((Number) o).floatValue();
		}else if(Decodable.class.isAssignableFrom(type)){			//decodable
			if(o == null){ return null; }
			Decodable d;
			try {
				d = (Decodable) MetaClass.create(type).createInstance();
			} catch (MetaClass.ConstructorNotFoundException e) {
				throw new DatabaseException("Cannot decode Decodable; no empty constructor: " + type.getName());
			}
			return d.decode(o.toString(), null);
		}else if(isEnum(type)){                                   //enumerable
			if(o == null){ return null; }
			return str2enum(type, o.toString());
		}else if(java.util.Date.class.isAssignableFrom(type)){    //date
			if(o == null){ return null; }
			if(o instanceof java.sql.Timestamp){
				return new java.util.Date( ((java.sql.Timestamp) o).getTime() );
			} else if(o instanceof java.sql.Date){
				return new java.util.Date( ((java.sql.Date) o).getTime() );
			}else{
				throw new DatabaseException("Unknown SQL 'date' type: " + o.getClass());
			}
		}else if(java.util.Date.class.isAssignableFrom(type)){    //calendar
			if(o == null){ return null; }
			if(o instanceof java.sql.Timestamp){
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTime(new Date( ((java.sql.Timestamp) o).getTime() ));
				return cal;
			} else if(o instanceof java.sql.Date){
				GregorianCalendar cal = new GregorianCalendar();
				cal.setTime(new Date( ((java.sql.Date) o).getTime() ));
				return cal;
			}else{
				throw new DatabaseException("Unknown SQL 'calendar' type: " + o.getClass());
			}
		}else if(type.isArray()){                                  //array
			if(o == null){ return null; }
			String[] strings = Utils.decodeArray(o.toString());
			if(isEnum(type.getComponentType())){
				Object[] rtn = (Object[]) Array.newInstance(type.getComponentType(), strings.length);
				for(int i=0; i<rtn.length; i++){
					rtn[i] = str2enum(type.getComponentType(), strings[i]);
				}
				return rtn;
			}else if(type.getComponentType().isAssignableFrom(String.class)){
				return strings;
			}else{
				Object dst = Array.newInstance(type.getComponentType(), strings.length);
				Object[] src = new Array[strings.length];
				for(int i=0; i<src.length; i++){
					src[i] = Utils.cast(strings[i], type.getComponentType());
				}
				Utils.arraycopy(src,0,dst,0,type.getComponentType(),strings.length);
				return dst;
			}
		} else if(Class.class.isAssignableFrom(type)){
			try {
				return Class.forName(o.toString());
			} catch (ClassNotFoundException e) {
				throw new DatabaseException("Class no longer exists in classpath: " + o);
			}
		}else{														//else
			return o;
		}
	}

	@SuppressWarnings({"unchecked"})
	private <E> E castResult(ResultSet results, int index, Class<E> clazz) throws SQLException{
		Object o = results.getObject(index+1);
		if(o instanceof byte[]){
			//(object stream)
			return (E) Utils.bytes2obj((byte[]) o);
		}else{
			//(native type)
			if(type == SQLITE){
				return (E) Utils.cast(o == null ? null : o.toString(), clazz);	//sqlite has no types
			}else{
				if(o instanceof BigInteger){
					long tmp = ((BigInteger) o).longValue();
					if(tmp < Integer.MAX_VALUE){
						o = (int) tmp;
					}else{
						o = tmp;
					}
				}
				return (E) db2obj(clazz, o);
			}
		}
	}
	
	private <E extends DatabaseObject> E populateObject(DBClassInfo<E> info, ResultSet results, E instance){
		try {
			Field[] fields = info.fields;
			for(int i=0; i<fields.length; i++){
				Field f = fields[i];
				if(f.getAnnotation(Parent.class) != null){ continue; }
				boolean accessible = true;
				if(!f.isAccessible()){
					f.setAccessible(true);
					accessible = false;
				}
				f.set(instance, castResult(results, i, f.getType()));
				if(!accessible){ f.setAccessible(false); }
			}
			return instance;
		} catch (IllegalArgumentException e) {
			throw new DatabaseException(e);
		} catch (IllegalAccessException e) {
			throw new DatabaseException(e);
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private <E extends DatabaseObject> DBClassInfo<E> ensureClassInfo(Class<E> clazz){
		DBClassInfo cand = DatabaseObject.getInfo(clazz, this);
		if(cand != null){
			return cand;
		} else {
			return emptyObject(clazz).getInfo();
		}
	}
	
	@Override
	public Decodable decode(String encoded, Type[] typeParams) {
		if(this.isConnected()){ throw new DatabaseException("Decoding into a connected database!"); }
		//--Clean String
		while(encoded.charAt(0) == '"' || encoded.charAt(0) == '\''){
			encoded = encoded.substring(1);
		}
		while(encoded.charAt(encoded.length()-1) == '"' || encoded.charAt(encoded.length()-1) == '\''){
			encoded = encoded.substring(0, encoded.length()-1);
		}
		//--Get Type
		int sep = encoded.indexOf("://");
		if(sep < 0){ throw new IllegalArgumentException("Invalid database string (no '://'): " + encoded); }
		String strType = encoded.substring(0, sep);
		encoded = encoded.substring(sep+3);
		if(strType.equalsIgnoreCase("psql") || 
					strType.equalsIgnoreCase("postgres") ||
					strType.equalsIgnoreCase("postgresql")){
			this.type = PSQL;	
		}else if(strType.equalsIgnoreCase("mysql")){
			this.type = MYSQL;
		}else if(strType.equalsIgnoreCase("sqlite")){
			this.type = SQLITE;
		}else{
			throw new IllegalArgumentException("Unknown database type: " + strType);
		}
		//--Get Arguments
		switch(this.type){
		case PSQL:
		case MYSQL:
			Matcher m = PATTERN_CONNINFO.matcher(encoded);
			if(!m.matches()){ throw new IllegalArgumentException("Bad format of connection string: " + encoded); }
			this.username = m.group(1);
			this.server = m.group(2);
			this.schema = m.group(4);
			this.password = m.group(5).substring(1);
			break;
		case SQLITE:
			this.sqlite = new File(encoded);
			if(!this.sqlite.exists()){ throw new IllegalArgumentException("No such sqlite file: " + encoded); }
			break;
		}
		return this;
	}
	
	public static Database fromString(String str){
		return (Database) (new Database()).decode(str,null);
	}

	@Override
	public String encode() {
		String cand = this.toString();
		if(this.password != null && !this.password.equals("")){
			return cand + "<" + this.password;
		}else{
			return cand;
		}
	}
	
	@Override
	public String toString(){
		switch(type){
		case MYSQL:
			return "mysql://"+username+"@"+server+":"+schema;
		case SQLITE:
			return "sqlite://" + sqlite.getPath();
		case PSQL:
			return "postgresql://"+username+"@"+server+":"+schema;
		default:
			throw new IllegalStateException("Unknown database type: " + type);
		}
	}
	
	private static final <E> boolean nonNative(Class <E> type){
		boolean n = type.isPrimitive() || 
				(Number.class.isAssignableFrom(type)) || 
				(Date.class.isAssignableFrom(type)) || 
				(String.class.isAssignableFrom(type)) ||
				isEnum(type) ||
				type.isArray();
		return !n;
	}
	
	private static final boolean isEnum(Class<?> clazz){
		if(clazz == null){ return false; }
		return Enum.class.isAssignableFrom(clazz) ||
			scala.Enumeration.Value.class.isAssignableFrom(clazz);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final Object str2enum(Class<?> clazz, String val){
		if(Enum.class.isAssignableFrom(clazz)){
			return Enum.valueOf((Class <? extends Enum>) clazz, val);
		}else if(scala.Enumeration.Value.class.isAssignableFrom(clazz)){
			try {
				return ((scala.Enumeration) clazz.getEnclosingClass().newInstance()).withName(val);
			} catch (InstantiationException e) {
				throw new DatabaseException(e);
			} catch (IllegalAccessException e) {
				throw new DatabaseException(e);
			}
//			try {
//				return clazz.getEnclosingClass().getMethod("valueOf").invoke(null,val);
//			} catch (IllegalArgumentException e) {
//				throw new DatabaseException(e);
//			} catch (SecurityException e) {
//				throw new DatabaseException(e);
//			} catch (IllegalAccessException e) {
//				throw new DatabaseException(e);
//			} catch (InvocationTargetException e) {
//				throw new DatabaseException(e);
//			} catch (NoSuchMethodException e) {
//				throw new DatabaseException(e);
//			}
		}else{
			throw new DatabaseException("Cannot convert to enum for class: " + clazz);
		}
	}
	

	private static String typeJava2sql(int databaseType, Field f, int length){
		return typeJava2sql(databaseType, f, length, java.lang.Object.class);
	}
	private static String typeJava2sql(int databaseType, Field f, int length, Class suggestedType){
		Class <?> clazz = (suggestedType != java.lang.Object.class) ? suggestedType : f.getType();
		if(f.getAnnotation(Parent.class) != null){
			return "INTEGER";
		}else if(clazz == boolean.class || Boolean.class.isAssignableFrom(clazz)){
			return "BOOLEAN";
		}else if(clazz == byte.class || Byte.class.isAssignableFrom(clazz)){
			return "SMALLINT";
		}else if(clazz == char.class || Character.class.isAssignableFrom(clazz)){
			return "SMALLINT";
		}else if(clazz == short.class || Short.class.isAssignableFrom(clazz)){
			return "SMALLINT";
		}else if(clazz == int.class || Integer.class.isAssignableFrom(clazz)){
			return "INTEGER";
		}else if(clazz == long.class || Long.class.isAssignableFrom(clazz)){
			return "NUMERIC(20)";
		}else if(clazz == double.class || Double.class.isAssignableFrom(clazz)){
			return "FLOAT8";
		}else if(clazz == float.class || Float.class.isAssignableFrom(clazz)){
			return "FLOAT4";
		}else if(Date.class.isAssignableFrom(clazz) || Calendar.class.isAssignableFrom(clazz)){
			switch(databaseType){
			case MYSQL:
				return "DATETIME";
			case PSQL:
			case SQLITE:
				return "TIMESTAMP";
			default:
				throw new DatabaseException("Unknown Database type: " + databaseType);
			}
		}else if(String.class.isAssignableFrom(clazz) || isEnum(clazz)){
			if(length < 0){
				return "TEXT";
			}else{
				return "VARCHAR(" + length + ")";
			}
		}else if(clazz.isArray() && String.class.isAssignableFrom(clazz.getComponentType()) ||
				isEnum(clazz.getComponentType())){
			if(length < 0){
				return "TEXT";
			}else{
				return "VARCHAR(" + length + ")";
			}
		}else if(Class.class.isAssignableFrom(clazz)){
			return "VARCHAR(127)";
		}else if(nonNative(clazz) && Serializable.class.isAssignableFrom(clazz)){
			if(databaseType == PSQL){
				return "BYTEA";
			}else{
				if(length < 0){ return "BLOB"; }
				else{ return "VARBINARY(" + length + ")"; }
			}
		}else if(Decodable.class.isAssignableFrom(clazz)){
			return "TEXT";
		}else{
			return "INTEGER"; //default: foreign key / primary key
		}
	}
	
	private static final String field2name(Field f){
		if(f.getAnnotation(Key.class) != null){
			return f.getAnnotation(Key.class).name();
		}else if(f.getAnnotation(PrimaryKey.class) != null){
			return f.getAnnotation(PrimaryKey.class).name();
		}else if(f.getAnnotation(Parent.class) != null){
			return f.getAnnotation(Parent.class).localField();
		}else{
			throw new DatabaseException("Field does not have a key on it: " + f);
		}
	}
	
}
