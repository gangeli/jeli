package org.goobs.tests;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.goobs.database.*;
import org.goobs.utils.Decodable;
import org.goobs.utils.MetaClass;
import org.goobs.utils.Utils;

public class DatabaseTest{

	/*
	 * Simple Mistakes
	 */
	@Table(name="tableEmpty")
	public static final class TableEmpty extends DatabaseObject{
	}
	@Table(name="tableFunkyNames")
	public static final class TableFunkyNames extends DatabaseObject{
		@Key(name="); DROP TABLE tableFunkyNames;")
		public String fieldStr4;
	}
	@Table(name="tablePrimaryKeyDuplicate")
	public static final class TablePrimaryKeyDuplicate extends DatabaseObject{
		@PrimaryKey(name="fieldPQ")
		public int pk;
		@PrimaryKey(name="fieldPQ2")
		public int pk2;
	}
	
	/*
	 * Datatypes
	 */
	@Table(name="tableFields")
	public static final class TableStandardFields extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Key(name="fieldString")
		public String fieldA;
		@Key(name="fieldBool")
		public boolean fieldBool;
		@Key(name="fieldShort")
		public short fieldShort;
		@Key(name="fieldInt")
		public int fieldInt;
		@Key(name="fieldLong")
		public long fieldLong;
		@Key(name="fieldDouble")
		public double fieldDouble;
		@Key(name="fieldFloat")
		public float fieldFloat;
		@Key(name="fieldChar")
		public char fieldChar;
		@Key(name="fieldByte")
		public byte fieldByte;
	}
	public static final class SomeSerializable implements Serializable{
		private static final long serialVersionUID = -7235965881349596805L;
		public String field;
		public SomeSerializable(String str){
			this.field = str;
		}
		public boolean equals(Object o){
			return (o instanceof SomeSerializable) && ((SomeSerializable) o).field.equals(field);
		}
	}
	public static final class SomeDecodable implements Decodable{
		public String field;
		public SomeDecodable(){}
		public SomeDecodable(String str){ this.field = str; }
		@Override
		public SomeDecodable decode(String encoded, Type[] typeParams) {
			this.field = encoded.substring(5);
			return this;
		}
		@Override
		public String encode() {
			return "junk+"+this.field;
		}
		@Override
		public String toString(){ return "SomeDecodable: " + (field==null ? "null" : field); }
		@Override
		public boolean equals(Object o){
			return (o instanceof SomeDecodable) && ((SomeDecodable) o).field.equals(field);
		}
	}
	@Table(name="tableSerializable")
	public static final class TableSerializable extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Key(name="fieldSerializable")
		public Serializable fieldSerializable;
	}
	@Table(name="tableSerializable2")
	public static final class TableSerializable2 extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Key(name="fieldSerializable", length=1024)
		public Serializable fieldSerializable;
	}
	@Table(name="tableDecodable")
	public static final class TableDecodable extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Key(name="fieldDecodable")
		public SomeDecodable fieldDecodable;
	}
	@Table(name="tablePrimaryKey")
	public static final class TablePrimaryKey extends DatabaseObject{
		@PrimaryKey(name="fieldString")
		public int pk;
	}
	@Table(name="tablePrimaryKeyPlus")
	public static final class TablePrimaryKeyPlus extends DatabaseObject{
		@Key(name="otherInt")
		public int str = -1;
		@PrimaryKey(name="pk")
		public int pk;
	}
	@Table(name="tableAllFields")
	public static final class TableAllFields extends DatabaseObject{
		@PrimaryKey(name="someId")
		public int id;
		@Key(name="fieldString")
		public String fieldString;
		@Key(name="fieldShort")
		public short fieldShort;
		@Key(name="fieldInt")
		public int fieldInt;
		@Key(name="fieldDouble")
		public double fieldDouble;
		@Key(name="fieldFloat")
		public float fieldFloat;
		@Key(name="fieldChar")
		public char fieldChar;
		@Key(name="fieldSerializable")
		public Serializable fieldSerializable;
		@Key(name="fieldDecodable")
		public Decodable fieldDecodable;
	}
	@Table(name="tableDate")
	public static final class TableDate extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Key(name="fieldDate")
		public Date fieldDate;
	}
	
	/*
	 * Indices
	 */
	
	@Table(name="tableindices")
	public static final class TableIndices extends DatabaseObject{
		@PrimaryKey
		public int id;
		
		@Index(type=Index.Type.BTREE) @Key(name="fieldA")
		public int fieldA;
		
		@IndexList({
			@Index(type=Index.Type.BTREE),
			@Index(type=Index.Type.HASH)
		})
		@Key(name="fieldB")
		public int fieldB;
		
		@IndexList({
			@Index(type=Index.Type.BTREE)
		})
		@Index(type=Index.Type.HASH) @Key(name="fieldC")
		public int fieldC;
		
		@Key(name="fieldNoIndex")
		public int noIndex;
	}
	
	@Table(name="tablePKNotint")
	public static final class TablePKNotint extends DatabaseObject{
		@PrimaryKey
		public String id;
	}
	
	@Table(name="tableCompoundIndices")
	@CompoundIndex(fields="fieldA,fieldB")
	@CompoundIndexList({
			@CompoundIndex(fields="fieldA,fieldB"),
			@CompoundIndex(fields="fieldA,fieldB"),
			@CompoundIndex(fields="fieldB,fieldC")
	})
	public static final class TableCompoundIndices extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Index @Key(name="fieldA")
		public int fieldA;
		@Index @Key(name="fieldB")
		public int fieldB;
		@Index @Key(name="fieldC")
		public int fieldC;
		@Key(name="fieldNoIndex")
		public int noIndex;
	}
	
	/*
	 * Enums
	 */
	
	public enum SomeEnum{
		ValueA,
		ValueB,
		ValueC,
		ValueD,
		Thisisaverylongvaluethatisintendedtomessupthesystembyoverflowingthevarcharbuffer,
	}
	@Table(name="tableEnums")
	public static final class TableEnums extends DatabaseObject{
		@PrimaryKey(name="nid")
		public int id;
		@Index @Key(name="fieldEnum",length=256)	//index, length
		public SomeEnum fieldEnum;
		@Key(name="fieldEnum2")						//no index, no length
		public SomeEnum fieldEnum2;
		@Key(name="fieldEnumArray")
		public SomeEnum[] fieldEnumArray;
		@Key(name="fieldStringArray")
		public String[] fieldStringArray;
		@Override
		public boolean equals(Object o){ return (o instanceof TableEnums) && ((TableEnums) o).fieldEnum == fieldEnum; }
	}
	
	/*
	 * Super / Subclass Behavior
	 */
	
	@Table(name="shouldNotExist")
	public static class TableSuper extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Key(name="fieldSuper")
		public int fieldSuper;
	}
	
	@Table(name="tableImplOne")
	public static final class TableImplOne extends TableSuper{
		@Key(name="fieldImpl")
		public int fieldImpl;
	}
	
	@Table(name="tableImplCompoundIndex")
	@CompoundIndex(fields="fieldSuper,fieldImpl")
	public static final class TableImplCompoundIndex extends TableSuper{
		@Key(name="fieldImpl")
		public int fieldImpl;
	}
	
	@Table(name="tableImplFailPK")
	public static final class TableImplFailPK extends TableSuper{
		@PrimaryKey
		public int id2;
	}
	
	/*
	 * Dynamic class changing
	 */
	
	@Table(name="tableChange")
	public static final class TableChange extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Key(name="fieldOne")
		public int fieldOne;
	}
	
	@Table(name="tableChange")	//note: same as above
	public static final class TableChangeLarger extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Key(name="fieldOne")
		public int fieldOne;
		@Key(name="fieldTwo")
		public int fieldTwo;
	}
	
	@Table(name="tableChange")	//note: same as above
	public static final class TableChangeSmaller extends DatabaseObject{
		@PrimaryKey
		public int id;
	}
	
	@Table(name="tableChange") //note: same as above
	public static final class TableChangeBad extends DatabaseObject{
		@PrimaryKey(name="idDifferent")
		public int idDifferent;	//different primary key
	}
	
	@Table(name="tableChange") //note: same as above
	public static final class TableChangeBad2 extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Key(name="fieldOne")
		public double fieldOne;	//different field type
	}
	
	@Table(name="tableChange")
	@CompoundIndexList({
		@CompoundIndex(fields="fieldOne,fieldTwo"),
		@CompoundIndex(fields="id,fieldTwo")
	})
	@CompoundIndex(fields="fieldOne,fieldTwo")
	public static final class TableChangeIndices extends DatabaseObject{
		@PrimaryKey
		public int id;
		@IndexList({
			@Index(type=Index.Type.HASH),
			@Index(type=Index.Type.HASH)
		})
		@Key(name="fieldOne")
		public double fieldOne;	//different field type
		@Index(type=Index.Type.HASH) @Key(name="fieldTwo")
		public double fieldTwo;	//different field type
	}
	
	/*
	 * Foreign Key Behavior
	 */
	@Table(name="TableFKBase")
	public static final class TableFKBase extends DatabaseObject{
		@PrimaryKey
		public int id;
		@Child(localField="id", childField="pid")
		public TableFKRef1[] sub1;
		@Child(localField="id", childField="pid")
		public TableFKRef2 sub2;
		@Parent(localField="parent", parentField="id")
		public TableFKBase parent;
		@Child(localField="id", childField="parent")
		public TableFKBase child;
	}
	@Table(name="TableFKRef1")
	public static final class TableFKRef1 extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Parent(localField="pid", parentField="id")
		public TableFKBase pid;
		@Child(localField="id", childField="pid")
		public TableFKRef1_1[] sub1;
		@Key(name="val")
		public String val;
		public TableFKRef1(String val, TableFKRef1_1 ...subs){ this.val = val; this.sub1 = subs; }
		public TableFKRef1(){}
	}
	@Table(name="TableFKSubRef1")
	public static final class TableFKRef1_1 extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Parent(localField="pid", parentField="id")
		public TableFKRef1 pid;
		@Key(name="val")
		public String val;
		public TableFKRef1_1(String val){ this.val = val; }
		public TableFKRef1_1(){}
	}
	@Table(name="TableFKRef2")
	public static final class TableFKRef2 extends DatabaseObject{
		@PrimaryKey(name="id")
		public int id;
		@Parent(localField="pid", parentField="id")
		public TableFKBase pid;
		@Key(name="val")
		public String val;
		public TableFKRef2(String val){ this.val = val; }
		public TableFKRef2(){}
	}
	
	

	
	
	
	
	private String server = "localhost";
	private String username = "java";
	private String password = "what?why42?";
	private String schema = "junit";
	
	private List<Database> eachType(){ return eachType(false,true); }
	private List<Database> eachType(boolean force ){ return eachType(true,true); }
	private List<Database> eachTypeDisconnected(){ return eachType(false,false); }
	private List<Database> eachType(boolean force, boolean connect){
		//(get connection types)
		Database.ConnInfo mysql = Database.ConnInfo.mysql(server, username, password, schema);
		Database.ConnInfo psql = Database.ConnInfo.psql(server, username, password, schema);
		File f = null;
		try {
			f = File.createTempFile("junit", ".db");
		} catch (IOException e) {
			fail(e.getMessage());
		}
		Database.ConnInfo sqlite = Database.ConnInfo.sqlite(f);
		//(create list)
		ArrayList <Database> rtn = new ArrayList <Database> ();
		try {
			rtn.add(connect ? new Database(mysql).connect() : new Database(mysql));
		} catch (Exception e) {
			if(force){ fail("Cannot create MySQL: " + e.getMessage()); }
		}
		try {
			rtn.add(connect ? new Database(psql).connect() : new Database(psql));
		} catch (Exception e) {
			if(force){ fail("Cannot create Postgresql: " + e.getMessage()); }
		}
		try {
			rtn.add(connect ? new Database(sqlite).connect() : new Database(sqlite));
		} catch (Exception e) {
			if(force){ fail("Cannot create sqlite: " + e.getMessage()); }
		}
		//(return)
		return rtn;
	}
	
	@Test
	public void haveAll(){
		eachType(true);
	}
	
	@Test
	public void database() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.disconnect();
		}
	}

	@Test
	public void connect() {
		for(Database d : eachTypeDisconnected()){
			assertFalse(d.isConnected());
			d.connect();
			assertTrue(d.isConnected());
			d.disconnect();
			assertTrue(!d.isConnected());
		}
	}

	private void createTableInternal(boolean shouldWork, Database d, Class<? extends DatabaseObject> table){
		if(shouldWork){
			//--Check Creation
			assertTrue(d.ensureTable(table));
			assertTrue(d.hasTable(table.getAnnotation(Table.class).name()));
			assertTrue(d.ensureTable(table));
			//--Check Fields
			String[] names = d.getTableColumns(table.getAnnotation(Table.class).name());
			int num = 0;
			for(Field f : table.getDeclaredFields()){
				for(Annotation ann : f.getAnnotations()){
					if(ann instanceof PrimaryKey){
						num += 1;
						assertTrue( Utils.contains(names, ((PrimaryKey) ann).name().trim().toLowerCase()) );
					}else if(ann instanceof Parent){
						num += 1;
						assertTrue( Utils.contains(names, ((Parent) ann).localField().trim().toLowerCase()) );
					}else if(ann instanceof Key){
						num += 1;
						assertTrue( Utils.contains(names, ((Key) ann).name().trim().toLowerCase()) );
					}else{
					}
				}
			}
			assertEquals(num, names.length);
		}else{
			try{
				d.ensureTable(table);
				assertFalse("Should not be able to make table: " + table,true);
			}catch(DatabaseException e){
				assertTrue("should throw a first-order exception, not: " + e.getCause(), e.getCause() == null);
			}
		}
	}
	
	@Test
	public void ensureTable() {
		for(Database d : eachType()){
			//--Check Connection
			assertTrue(d.isConnected());
			d.clear();
			//--Check Tables
			createTableInternal(false, d, TableEmpty.class);
			createTableInternal(false, d, TableFunkyNames.class);
			createTableInternal(false, d, TablePrimaryKeyDuplicate.class);
			createTableInternal(false, d, TablePrimaryKey.class);
			createTableInternal(true, d, TableStandardFields.class);
			createTableInternal(true, d, TablePrimaryKeyPlus.class);
			createTableInternal(true, d, TableIndices.class);
			createTableInternal(false, d, TablePKNotint.class);
			d.disconnect();
		}
	}
	
	@Test
	public void clear(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			assertTrue(d.getTableNames().length == 0);
			d.ensureTable(TableStandardFields.class);
			assertTrue(d.getTableNames().length == 1);
			d.clear();
			assertTrue(d.getTableNames().length == 0);
			d.disconnect();
		}
	}
	
	@Test
	public void autoincrement(){
		for(Database d : eachType()){
			//--Create Connection
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TablePrimaryKeyPlus.class);
			//--Check AutoIncrement
			//(setup)
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			//(check)
			TablePrimaryKeyPlus x = d.getObjectById(TablePrimaryKeyPlus.class, 1);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(x.pk, 1);
			x = d.getObjectById(TablePrimaryKeyPlus.class, 2);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(x.pk, 2);
			x = d.getObjectById(TablePrimaryKeyPlus.class, 3);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(x.pk, 3);
			x = d.getObjectById(TablePrimaryKeyPlus.class, 4);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(x.pk, 4);
			x = d.getObjectById(TablePrimaryKeyPlus.class, 5);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(x.pk, 5);
			assertEquals(5, d.getTableRowCount(TablePrimaryKeyPlus.class));
			//--Disconnect
			d.disconnect();
		}
	}
	
	@Test
	public void fieldsBasic(){
		for(Database d : eachType()){
			//(setup)
			d.clear();
			assertTrue(d.ensureTable(TableStandardFields.class));
			//(save)
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "string";
			f.fieldBool = true;
			f.fieldChar = 'c';
			f.fieldDouble = 3.1415926535;
			f.fieldFloat = 3.14159f;
			f.fieldInt = 12345667;
			f.fieldShort = 42;
			f.fieldByte = 7;
			f.fieldLong = 4000000000123L;
			f.flush();
			//(load)
			f = d.getObjectById(TableStandardFields.class, f.id);
			assertTrue(f != null);
			TableStandardFields g = d.getObjectById(TableStandardFields.class, f.id);
			assertTrue("Strict equality should hold between two identical reads", f == g);
			//(check)
			assertTrue(f.id != 0);
			assertEquals(f.fieldA, "string");
			assertEquals(f.fieldBool, true);
			assertEquals(f.fieldChar, 'c');
			assertEquals(f.fieldDouble, 3.1415926535, 0.0);
			assertEquals(f.fieldFloat, 3.14159f, 0.0);
			assertEquals(f.fieldInt, 12345667);
			assertEquals(f.fieldShort, 42);
			assertEquals(f.fieldByte, 7);
			assertEquals(f.fieldLong, 4000000000123L);
			
			//(where primary key not first field)
			TablePrimaryKeyPlus x = d.emptyObject(TablePrimaryKeyPlus.class);
			x.pk = 0;
			x.flush();
			assertTrue(x.pk != 0);
			TablePrimaryKeyPlus y = d.getObjectById(TablePrimaryKeyPlus.class, x.pk);
			assertTrue( x == y );
			
			d.disconnect();
		}
	}
	
	/*
	 * NOTE: times are accurate to the nearest second
	 */
	@SuppressWarnings("deprecation")
	@Test
	public void fieldsDate(){
		for(Database d : eachType()){
			//(setup)
			d.clear();
			assertTrue(d.ensureTable(TableDate.class));
			//(save)
			Date date = new Date(System.currentTimeMillis());
			TableDate f = d.emptyObject(TableDate.class);
			f.fieldDate = date;
			f.flush();
			//(load)
			f = d.getObjectById(TableDate.class, f.id);
			assertNotNull(f);
			//(check)
			assertTrue(f.id != 0);
			assertNotNull("Date null for db: " + d, f.fieldDate);
			assertTrue("Should return java.util.Date", 
					f.fieldDate.getClass().equals(java.util.Date.class));
			assertTrue("Timestamps must be within a second of each other: " + f.fieldDate + " and " + date,
					Math.abs( f.fieldDate.getTime() - date.getTime() ) < 1000);
			//(check weird dates)
			date = new Date(1929,10,29,0,0);	//hope this date does better than the market and doesn't crash
			f = d.emptyObject(TableDate.class);
			f.fieldDate = date;
			f.flush();
			//(load)
			f = d.getObjectById(TableDate.class, f.id);
			assertNotNull(f);
			assertTrue(f.id != 0);
			assertNotNull("Date null for db: " + d, f.fieldDate);
			assertTrue("Should return java.util.Date", 
					f.fieldDate.getClass().equals(java.util.Date.class));
			assertTrue("Timestamps must be within a second of each other",
					Math.abs( f.fieldDate.getTime() - date.getTime() ) < 1000);
			d.disconnect();
		}
	}
	
	private void floatTest(Database d, double val, double exp){
		float fval = (float) val;
		float fexp = (float) exp;
		//(save)
		TableStandardFields f = d.emptyObject(TableStandardFields.class);
		f.fieldFloat = fval;
		f.fieldDouble = val;
		f.flush();
		//(load)
		f = d.getObjectById(TableStandardFields.class, f.id);
		assertTrue(f != null);
		//(check)
		if(Double.isNaN(exp)){
			assertTrue("Not NaN: " + f.fieldDouble, Double.isNaN(f.fieldDouble));
		}else{
			assertEquals(exp, f.fieldDouble, 0.0001);
		}
		if(Float.isNaN(fexp)){
			assertTrue(Float.isNaN(f.fieldFloat));
		} else {
			assertEquals(fexp, f.fieldFloat, 0.0001);
		}
	}
	
	private void intTest(Database d, long val){
		byte bval = (byte) val;
		char cval = (char) val;
		short sval = (short) val;
		int ival = (int) val;
		//(save)
		TableStandardFields g = d.emptyObject(TableStandardFields.class);
		g.fieldByte = bval;
		g.fieldChar = cval;
		g.fieldShort = sval;
		g.fieldInt = ival;
		g.fieldLong = val;
		g.flush();
		//(load)
		TableStandardFields f = d.getObjectById(TableStandardFields.class, g.id);
		assertTrue(f != null);
		assertTrue(f == g);
		//(check)
		assertEquals(bval, f.fieldByte);
		assertEquals(cval, f.fieldChar);
		assertEquals(sval, f.fieldShort);
		assertEquals(ival, f.fieldInt);
		assertEquals( val, f.fieldLong);
	}
	
	/*
	 * TODO doubles act strange on corner cases
	 * 		e.g.
	 * 		psql MIN_VALUE (exception)
	 * 		sqlite MAX_VALUE (pos_infinity)
	 * Note: double values are not guaranteed to be returned exactly
	 */
	@Test
	public void fieldsRanges(){
		for(Database d : eachType()){
			//(setup)
			d.clear();
			assertTrue(d.ensureTable(TableStandardFields.class));
			//--Lower Bounds
			//(save)
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "";
			f.fieldChar = Character.MIN_VALUE;
			f.fieldFloat = Float.MIN_VALUE;
			f.fieldInt = Integer.MIN_VALUE;
			f.fieldShort = Short.MIN_VALUE;
			f.fieldByte = Byte.MIN_VALUE;
			f.fieldLong = Long.MIN_VALUE;
			f.flush();
			//(load)
			f = d.getObjectById(TableStandardFields.class, f.id);
			assertTrue(f != null);
			//(check)
			assertEquals("", f.fieldA);
			assertEquals(Character.MIN_VALUE, f.fieldChar);
			assertEquals(Float.MIN_VALUE, f.fieldFloat, 0.0);
			assertEquals(Integer.MIN_VALUE, f.fieldInt);
			assertEquals(Short.MIN_VALUE, f.fieldShort);
			assertEquals(Byte.MIN_VALUE, f.fieldByte);
			assertEquals(Long.MIN_VALUE, f.fieldLong);
			//--Upper Bounds
			//(save)
			f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "";
			f.fieldChar = Character.MAX_VALUE;
			f.fieldFloat = Float.MAX_VALUE;
			f.fieldInt = Integer.MAX_VALUE;
			f.fieldShort = Short.MAX_VALUE;
			f.fieldByte = Byte.MAX_VALUE;
			f.fieldLong = Long.MAX_VALUE;
			f.flush();
			//(load)
			f = d.getObjectById(TableStandardFields.class, f.id);
			assertTrue(f != null);
			//(check)
			assertEquals("", f.fieldA);
			assertEquals(Character.MAX_VALUE, f.fieldChar);
			assertEquals(Float.MAX_VALUE, f.fieldFloat, 0.0);
			assertEquals(Integer.MAX_VALUE, f.fieldInt);
			assertEquals(Short.MAX_VALUE, f.fieldShort);
			assertEquals(Byte.MAX_VALUE, f.fieldByte);
			assertEquals(Long.MAX_VALUE, f.fieldLong);
			//--Special Cases
			floatTest(d,Double.NaN, Double.NaN);
			floatTest(d,Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
			floatTest(d,Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
			//--Random tests
			Random r = new Random();
			for(int i=0; i<10; i++){
				double fl = r.nextDouble();
				long l = r.nextLong();
				floatTest(d,fl,fl);
				intTest(d,l);
			}
			d.disconnect();
		}
	}
	
	@Test
	public void transaction() {
		for(Database d : eachType()){
			//--Create Connection
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableStandardFields.class);
			d.beginTransaction();
			for(int i=0; i<1000; i++){
				TableStandardFields f = d.emptyObject(TableStandardFields.class);
				f.fieldA = "string";
				f.fieldChar = 'c';
				f.fieldDouble = 3.1415926535;
				f.fieldFloat = 3.14159f;
				f.fieldInt = 12345667;
				f.fieldShort = 42;
				f.fieldByte = 7;
				f.fieldLong = 4000000000123L;
				f.flush();
			}
			d.endTransaction();
			//--Disconnect
			d.disconnect();
		}
	}

	@Test
	public void getTableColumns() {
		for(Database d : eachType()){
			//--Create Connection
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableStandardFields.class);
			//--Check columns
			String[] names = d.getTableColumns(TableStandardFields.class.getAnnotation(Table.class).name());
			int num = 0;
			for(Field f : TableStandardFields.class.getDeclaredFields()){
				//--Add It's Annotation
				for(Annotation ann : f.getAnnotations()){
					if(ann instanceof PrimaryKey){
						num += 1;
						assertTrue( Utils.contains(names, ((PrimaryKey) ann).name().trim().toLowerCase()) );
					}else if(ann instanceof Parent){
						num += 1;
						assertTrue( Utils.contains(names, ((Parent) ann).localField().trim().toLowerCase()) );
					}else if(ann instanceof Key){
						num += 1;
						assertTrue( Utils.contains(names, ((Key) ann).name().trim().toLowerCase()) );
					}else{
					}
				}
			}
			assertEquals(num, names.length);
			d.disconnect();
		}
	}

	/**
	 * Test using emptyObject() calls
	 */
	@Test
	public void createObject1() {
		for(Database d : eachType()){
			//(create connection)
			assertTrue(d.isConnected());
			d.clear();
			//(case: table exists)
			d.ensureTable(TableStandardFields.class);
			DatabaseObject o = d.emptyObject(TableStandardFields.class);
			assertTrue(((TableStandardFields) o).id == 0);
			o.flush();
			assertTrue(((TableStandardFields) o).id > 0);
			//(case: table doesn't exist)
			o = d.emptyObject(TablePrimaryKeyPlus.class);
			assertTrue(((TablePrimaryKeyPlus) o).pk == 0);
			o.flush();
			assertTrue("" + ((TablePrimaryKeyPlus) o).pk , ((TablePrimaryKeyPlus) o).pk > 0);
		}
	}
	
	/**
	 * Test using registerType()+registerObject() calls
	 */
	@Test
	public void createObject2() {
		for(Database d : eachType()){
			//(create connection)
			assertTrue(d.isConnected());
			d.clear();
			//(case: table exists)
			d.ensureTable(TableStandardFields.class);
			DatabaseObject o = new TableStandardFields();
			d.registerType(TableStandardFields.class);
			d.registerObject(o);
			assertTrue(((TableStandardFields) o).id == 0);
			o.flush();
			assertTrue(((TableStandardFields) o).id > 0);
			//(case: table doesn't exist)
			o = new TablePrimaryKeyPlus();
			d.registerType(TablePrimaryKeyPlus.class);
			d.registerObject(o);
			assertTrue(((TablePrimaryKeyPlus) o).pk == 0);
			o.flush();
			assertTrue("" + ((TablePrimaryKeyPlus) o).pk, ((TablePrimaryKeyPlus) o).pk > 0);
			//(case: forgot to register)
			o = new TableAllFields();
			try{
				d.registerObject(o);
				assertTrue("Should not be able to register object before registering type", false);
			}catch(DatabaseException e){
				assertTrue(e.getCause() == null);
			}
		}
	}

	@Test
	public void getFirstObject() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Create Database
			d.ensureTable(TableStandardFields.class);
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "hello world";
			f.flush();
			//--Tests
			//(everything)
			TableStandardFields x = d.getFirstObject(TableStandardFields.class, 
				"SELECT * FROM " + TableStandardFields.class.getAnnotation(Table.class).name() + ";");
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			//(string search)
			x = d.getFirstObject(TableStandardFields.class, 
					"SELECT * FROM " + TableStandardFields.class.getAnnotation(Table.class).name() 
					+ " WHERE fieldString='hello world';");
			assertTrue(x.isInDatabase());
			assertTrue(x != null);
			assertEquals("hello world", x.fieldA);
			d.disconnect();
		}
	}

	@Test
	public void getObjects() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Create Database
			d.ensureTable(TableStandardFields.class);
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "hello world";
			f.flush();
			//--Tests
			//(everything)
			Iterator<TableStandardFields> x = d.getObjects(TableStandardFields.class, 
				"SELECT * FROM " + TableStandardFields.class.getAnnotation(Table.class).name());
			int count = 0;
			assertTrue(x != null);
			d.getObjectById(TableStandardFields.class, 1); //don't have to use iterator right away
			d.getObjects(TableStandardFields.class, 
				"SELECT * FROM " + TableStandardFields.class.getAnnotation(Table.class).name());
			while(x.hasNext()){
				f = x.next();
				assertTrue(f != null);
				assertTrue(f.isInDatabase());
				assertTrue(f.id > 0);
				count += 1;
			}
			assertEquals(6, count);
			assertEquals(6, d.getTableRowCount(TableStandardFields.class));
			//(string search)
			x = d.getObjects(TableStandardFields.class, 
					"SELECT * FROM " + TableStandardFields.class.getAnnotation(Table.class).name() 
					+ " WHERE fieldString='hello world'");
			count = 0;
			assertTrue(x != null);
			while(x.hasNext()){
				f = x.next();
				assertTrue(f != null);
				assertTrue(f.isInDatabase());
				assertTrue(f.id > 0);
				assertEquals("hello world", f.fieldA);
				count += 1;
			}
			assertEquals(1, count);
			
			d.disconnect();
		}
	}

	@Test
	public void getFirstObjectWhere() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Create Database
			d.ensureTable(TableStandardFields.class);
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "hello world";
			f.flush();
			//--Tests
			//(everything)
			TableStandardFields x = d.getFirstObjectWhere(TableStandardFields.class, "");
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			x = d.getFirstObjectWhere(TableStandardFields.class, null);
			assertTrue(x != null);
			//(string search)
			x = d.getFirstObjectWhere(TableStandardFields.class,"fieldString='hello world'");
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals("hello world", x.fieldA);
			d.disconnect();
		}
	}

	@Test
	public void getObjectsWhere() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Create Database
			d.ensureTable(TableStandardFields.class);
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			d.emptyObject(TableStandardFields.class).flush();
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.fieldA = "hello world";
			f.flush();
			//--Tests
			//(everything)
			Iterator<TableStandardFields> x = d.getObjectsWhere(TableStandardFields.class, "");
			int count = 0;
			assertTrue(x != null);
			while(x.hasNext()){
				f = x.next();
				assertTrue(f != null);
				assertTrue(f.isInDatabase());
				assertTrue(f.id > 0);
				count += 1;
			}
			assertEquals(6, count);
			assertEquals(6, d.getTableRowCount(TableStandardFields.class));
			//(string search)
			x = d.getObjectsWhere(TableStandardFields.class, "fieldString='hello world'");
			count = 0;
			assertTrue(x != null);
			while(x.hasNext()){
				f = x.next();
				assertTrue(f != null);
				assertTrue(f.isInDatabase());
				assertTrue(f.id > 0);
				assertEquals("hello world", f.fieldA);
				count += 1;
			}
			assertEquals(1, count);
			
			d.disconnect();
		}
	}

	@Test
	public void getObjectById() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TablePrimaryKeyPlus.class);
			//(setup)
			d.emptyObject(TablePrimaryKeyPlus.class).flush();
			//(check)
			TablePrimaryKeyPlus x = d.getObjectById(TablePrimaryKeyPlus.class, 1);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			//--Disconnect
			d.disconnect();
		}
	}

	@Test
	public void getObjectByKey() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableIndices.class);
			//(setup)
			d.beginTransaction();
			TableIndices x = d.emptyObject(TableIndices.class);
			x.fieldA = -42;
			x.fieldB = 42;
			x.noIndex = 9;
			x.flush();
			Random r = new Random();
			for(int i=0; i<10000; i++){
				x = d.emptyObject(TableIndices.class);
				x.fieldA = r.nextInt();
				x.fieldB = r.nextInt();
				x.noIndex = r.nextInt();
				x.flush();
			}
			d.endTransaction();
			//(check)
			x = d.getObjectByKey(TableIndices.class, "fieldA", -42);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(-42, x.fieldA);
			x = d.getObjectByKey(TableIndices.class, "fieldB", 42);
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(42, x.fieldB);
			//--Disconnect
			d.disconnect();
		}
	}

	@Test
	public void gGetObjectsByKey() {
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableIndices.class);
			//(setup)
			d.beginTransaction();
			TableIndices x = d.emptyObject(TableIndices.class);
			x.fieldA = -42;
			x.fieldB = 42;
			x.noIndex = 9;
			x.flush();
			Random r = new Random();
			for(int i=0; i<10000; i++){
				x = d.emptyObject(TableIndices.class);
				x.fieldA = r.nextInt();
				x.fieldB = r.nextInt();
				x.noIndex = r.nextInt();
				x.flush();
			}
			d.endTransaction();
			//(check)
			Iterator<TableIndices> iter = d.getObjectsByKey(TableIndices.class, "fieldA", -42);
			while(iter.hasNext()){
				x = iter.next();
				assertTrue(x != null);
				assertTrue(x.isInDatabase());
				assertEquals(-42, x.fieldA);
			}
			iter = d.getObjectsByKey(TableIndices.class, "fieldB", 42);
			while(iter.hasNext()){
				x = iter.next();
				assertTrue(x != null);
				assertTrue(x.isInDatabase());
				assertEquals(42, x.fieldB);
			}
			//--Disconnect
			d.disconnect();
		}
	}
	
	@Test
	public void serializableClasses(){
		for(Database d : eachType()){
			if(!d.isLite()){	//Note: sqlite doesn't support serializables
				assertTrue(d.isConnected());
				d.clear();
				//--Basic Test
				d.ensureTable(TableSerializable.class);
				TableSerializable t = d.emptyObject(TableSerializable.class);
				SomeSerializable s = new SomeSerializable("Hello world");
				t.fieldSerializable = s;
				t.flush();
				assertTrue(t.id > 0);
				t = d.getObjectById(TableSerializable.class,t.id);
				assertEquals(s, t.fieldSerializable);
				//--Length Given Test
				//--Basic Test
				d.ensureTable(TableSerializable2.class);
				TableSerializable2 t2 = d.emptyObject(TableSerializable2.class);
				s = new SomeSerializable("Hello world");
				t2.fieldSerializable = s;
				t2.flush();
				assertTrue(t.id > 0);
				t2 = d.getObjectById(TableSerializable2.class,t.id);
				assertEquals(s, t2.fieldSerializable);
				d.disconnect();
			}
		}
	}
	
	@Test
	public void decodableClasses(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Basic Test
			d.ensureTable(TableDecodable.class);
			TableDecodable t = d.emptyObject(TableDecodable.class);
			SomeDecodable s = new SomeDecodable("Hello world");
			t.fieldDecodable = s;
			t.flush();
			assertTrue(t.id > 0);
			t = d.getObjectById(TableDecodable.class,t.id);
			assertEquals(s, t.fieldDecodable);
			d.disconnect();
		}
	}
	
	@Test
	public void update(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//(save)
			TableStandardFields f = d.emptyObject(TableStandardFields.class);
			f.flush();
			//--Random Updates
			SecureRandom sr = new SecureRandom();
			Random r = new Random();
			for(int i=0; i<(d.isLite() ? 10 : 1000); i++){
				//(ensure update versus insert)
				Iterator<TableStandardFields> iter = d.getObjectsWhere(TableStandardFields.class, "");
				assertTrue("Database should have an element", iter.hasNext());
				assertTrue("Sanity check: iter.hasNext() then iter.next()", iter.next() != null);
				assertFalse("Database should have exactly one element", iter.hasNext());
				//(get random data)
				byte newB = (byte) r.nextInt(Byte.MAX_VALUE);
				char newC = (char) r.nextInt(Byte.MAX_VALUE);
				short newS = (short) r.nextInt(Short.MAX_VALUE);
				int newI = r.nextInt();
				long newL = r.nextLong();
				float newF = r.nextFloat();
				double newD = r.nextDouble();
				String newStr = new BigInteger(130, sr).toString(32);
				//(set fields)
				f.fieldA = newStr;
				f.fieldChar = newC;
				f.fieldFloat = newF;
				f.fieldDouble = newD;
				f.fieldInt = newI;
				f.fieldShort = newS;
				f.fieldByte = newB;
				f.fieldLong = newL;
				//(update and load)
				f.flush();
				f = d.getObjectById(TableStandardFields.class, f.id);
				assertTrue(f != null);
				//(check)
				assertEquals(newStr, f.fieldA);
				assertEquals(newC, f.fieldChar);
				assertTrue(Math.abs(newF - f.fieldFloat) < 0.001);
				assertTrue(Math.abs(newD - f.fieldDouble) < 0.001);
				assertEquals(newI, f.fieldInt);
				assertEquals(newS, f.fieldShort);
				assertEquals(newB, f.fieldByte);
				assertEquals(newL, f.fieldLong);
			}
			//(disconnect)
			d.disconnect();
		}
	}
	
	@Test
	public void enums(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableEnums.class);
			//--Basic Test
			for(SomeEnum e : SomeEnum.values()){
				TableEnums x = d.emptyObject(TableEnums.class);
				x.fieldEnum = e;
				x.fieldEnum2 = e;
				x.flush();
				//(simple retrieve
				x = d.getObjectById(TableEnums.class, x.id);
				assertTrue(x != null);
				assertTrue(x.isInDatabase());
				assertTrue(x.fieldEnum == e);
				assertTrue(x.fieldEnum2 == e);
				x.fieldEnum2 = null;
				x.flush();
				//(by string query)
				x = d.getFirstObjectWhere(TableEnums.class, "fieldEnum='"+e.toString()+"'");
				assertTrue(x != null);
				assertTrue(x.isInDatabase());
				assertTrue(x.fieldEnum == e);
				assertTrue("" + x.fieldEnum2, x.fieldEnum2 == null);
			}
			//--Redo Search
			for(SomeEnum e : SomeEnum.values()){
				TableEnums x = null;
				//(by string query)
				x = d.getFirstObjectWhere(TableEnums.class, "fieldEnum='"+e.toString()+"'");
				assertTrue(x != null);
				assertTrue(x.isInDatabase());
				assertTrue(x.fieldEnum == e);
			}
			//--Enum Array
			for(int length = 0; length < 5; length++){
				TableEnums x = d.emptyObject(TableEnums.class);
				x.fieldEnumArray = new SomeEnum[length];
				for(int i=0; i<length; i++){
					x.fieldEnumArray[i] = SomeEnum.values()[new Random().nextInt(SomeEnum.values().length)];
				}
				x.flush();
				assertTrue(x.isInDatabase());
				SomeEnum[] expected = x.fieldEnumArray;
				x = d.getObjectById(TableEnums.class, x.id);
				assertNotNull(x);
				assertArrayEquals(expected, x.fieldEnumArray);
			}
			//--Enum Array vs String Array
			TableEnums x = d.emptyObject(TableEnums.class);
			x.fieldEnumArray = new SomeEnum[]{SomeEnum.ValueA, SomeEnum.ValueB};
			x.fieldStringArray = new String[]{SomeEnum.ValueB.toString(), SomeEnum.ValueC.toString()};
			SomeEnum[] expEnum = x.fieldEnumArray;
			String[] expStr = x.fieldStringArray;
			x.flush();
			assertTrue(x.isInDatabase());
			x = d.getObjectById(TableEnums.class, x.id);
			assertNotNull(x);
			assertArrayEquals(expEnum, x.fieldEnumArray);
			assertArrayEquals(expStr, x.fieldStringArray);
			//--Disconnect
			d.disconnect();
		}
	}
	
	@Test
	public void compoundIndices(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			d.ensureTable(TableIndices.class);
			//(setup)
			d.beginTransaction();
			TableCompoundIndices x = d.emptyObject(TableCompoundIndices.class);
			x.fieldA = -42;
			x.fieldB = 42;
			x.noIndex = 9;
			x.flush();
			Random r = new Random();
			for(int i=0; i<10000; i++){
				x = d.emptyObject(TableCompoundIndices.class);
				x.fieldA = r.nextInt();
				x.fieldB = r.nextInt();
				x.noIndex = r.nextInt();
				x.flush();
			}
			d.endTransaction();
			//(check)
			x = d.getFirstObjectWhere(TableCompoundIndices.class, "fieldA=-42 and fieldB=42");
			assertTrue(x != null);
			assertTrue(x.isInDatabase());
			assertEquals(-42, x.fieldA);
			assertEquals(42, x.fieldB);
			//--Disconnect
			d.disconnect();
		}
	}
	
	@Test
	public void subclassBehavior(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Test
			//(simple subclass)
			d.ensureTable(TableImplOne.class);
			String[] fields = d.getTableColumns(TableImplOne.class.getAnnotation(Table.class).name());
			assertEquals(3, fields.length);
			//(hybrid compound index)
			d.ensureTable(TableImplCompoundIndex.class);
			fields = d.getTableColumns(TableImplOne.class.getAnnotation(Table.class).name());
			assertEquals(3, fields.length);
			//(duplicate primary key)
			createTableInternal(false, d, TableImplFailPK.class);
			//(test storage/retreival)
			TableImplOne x = d.emptyObject(TableImplOne.class);
			x.fieldSuper = 42;
			x.fieldImpl = -42;
			x.flush();
			x = d.getObjectById(TableImplOne.class, x.id);
			assertEquals(42, x.fieldSuper);
			assertEquals(-42, x.fieldImpl);
			//--Disconnect
			d.disconnect();
		}
	}
	
	@Test
	public void decodeEncode(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			//(encode)
			String encoded = d.encode();
			d.disconnect();
			//(decode)
			d = new MetaClass(Database.class).createInstance();
			d.decode(encoded,null);
			//(test decoding)
			d.connect();
			assertTrue(d.isConnected());
			d.disconnect();
		}
	}
	
	@Test
	@Ignore //I'm not even sure we want this to work...
	public void dynamicChanging(){
		boolean saved = Database.FORCEDBUPDATE;
		Database.FORCEDBUPDATE = true;
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			d.clear();
			//--Test
			//(base class)
			d.ensureTable(TableChange.class);
			String[] fields = d.getTableColumns(TableChange.class.getAnnotation(Table.class).name());
			assertEquals(2, fields.length);
			//(create larger)
			TableChangeLarger larger = d.emptyObject(TableChangeLarger.class);
			larger.fieldOne = 1;
			larger.fieldTwo = 2;
			larger.flush();
			fields = d.getTableColumns(TableChange.class.getAnnotation(Table.class).name());
			assertEquals(3, fields.length);
			larger = d.getObjectById(TableChangeLarger.class, larger.id);
			assertEquals(1, larger.fieldOne);
			assertEquals(2, larger.fieldTwo);
			//(fit larger into original)
			TableChange change = d.getObjectById(TableChange.class, larger.id);
			assertEquals(1, change.fieldOne);
			//(create smaller)
			TableChangeSmaller smaller = d.emptyObject(TableChangeSmaller.class);
			smaller.flush();
			//(fit smaller into original)
			change = d.getObjectById(TableChange.class, smaller.id);
			assertEquals(0, change.fieldOne);	//null
			//(fit smaller into larger)
			larger = d.getObjectById(TableChangeLarger.class, smaller.id);
			assertEquals(0, larger.fieldOne);	//null
			assertEquals(0, larger.fieldTwo);	//null
			//(incompatible class: different primary key)
			TableChangeBad bad = d.emptyObject(TableChangeBad.class);
			try{
				bad.flush();
				assertTrue("Should not be able to merge incompatible object", false);
			}catch(DatabaseException e){
				assertTrue(e.getCause() == null);
			}
			//(incompatible class: different types)
			TableChangeBad2 bad2 = d.emptyObject(TableChangeBad2.class);
			try{
				bad2.flush();
				assertTrue("Should not be able to merge incompatible object", false);
			}catch(DatabaseException e){
				assertTrue(e.getCause() == null);
			}
			//(create new index)
			TableChangeIndices indices = d.emptyObject(TableChangeIndices.class);
			assertEquals(0, d.getTableIndices(TableChangeIndices.class.getAnnotation(Table.class).name()).length);
			indices.flush();
			assertEquals(6, d.getTableIndices(TableChangeIndices.class.getAnnotation(Table.class).name()).length);
			
			//--Disconnect
			d.disconnect();
		}
		Database.FORCEDBUPDATE = saved;
	}
	
	@Test
	public void testForeignKey(){	//TODO foreign keys still aren't very robust
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			//--Setup
			d.clear();
			//(create tables)
			d.ensureTable(TableFKBase.class);
			d.ensureTable(TableFKRef1.class);
			d.ensureTable(TableFKRef1_1.class);
			d.ensureTable(TableFKRef2.class);
			//(main)
			TableFKBase b1 = d.emptyObject(TableFKBase.class);
			TableFKBase b2 = d.emptyObject(TableFKBase.class);
			TableFKBase b3 = d.emptyObject(TableFKBase.class);
			//(sub1)
			TableFKRef1 r1_1 = d.emptyObject(TableFKRef1.class, "r1.1",
					d.emptyObject(TableFKRef1_1.class,"r1_1.1"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.2"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.3"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.4")
				);
			r1_1.pid = b1;
			assertEquals(0, d.getTableRowCount(TableFKRef1_1.class));
			r1_1.deepFlush();
			assertEquals(4, d.getTableRowCount(TableFKRef1_1.class));
			b1.flush();	//should not break
			//(mid-test: b1 should have flushed)
			assertNotNull(d.getObjectById(TableFKBase.class, b1.id));
			TableFKRef1 r1_2 = d.emptyObject(TableFKRef1.class, "r1.2",
					d.emptyObject(TableFKRef1_1.class,"r1_1.5"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.6"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.7"),
					d.emptyObject(TableFKRef1_1.class,"r1_1.8"));
			r1_2.pid = b1;
			r1_2.deepFlush();
			//(sub2)
			TableFKRef2 r2_1 = d.emptyObject(TableFKRef2.class, "r2.1");
			TableFKRef2 r2_2 = d.emptyObject(TableFKRef2.class, "r2.2").flush();
			TableFKRef2 r2_3 = d.emptyObject(TableFKRef2.class, "r2.3").flush();
			
			b1.sub1 = new TableFKRef1[]{r1_1, r1_2};
			b1.sub2 = r2_1;
			b1.parent = b2;
			//(flush)
			b3.flush();
			b2.flush();
			b1.flush(); //must flush after b2
			
			//--Tests
			//(explicit flushes should be in the database)
			assertNotNull(d.getObjectById(TableFKBase.class, b1.id));
			assertNotNull(d.getObjectById(TableFKBase.class, b2.id));
			assertNotNull(d.getObjectById(TableFKBase.class, b3.id));
			assertNotNull(d.getObjectById(TableFKRef1.class, r1_1.id));
			assertNotNull(d.getObjectById(TableFKRef1.class, r1_2.id));
			assertNotNull(d.getObjectById(TableFKRef2.class, r2_2.id));
			assertNotNull(d.getObjectById(TableFKRef2.class, r2_3.id));
			//(implicit flushes should not yet)
			assertEquals(2, d.getTableRowCount(TableFKRef2.class));
			assertNull(d.getObjectById(TableFKRef2.class, r2_1.id));
			//(now implicit flushes should too)
			b1.deepFlush();
			assertNotNull(d.getObjectById(TableFKRef2.class, r2_1.id));
			r2_1.flush(); //make sure this works too
			//(foreign relations should not be set)
			TableFKBase b1bak = b1;
//			d.disconnect(); //reset cache
//			d.connect();
			b1 = d.getObjectById(TableFKBase.class, b1.id);
//			assertNull(b1.parent);
//			assertNull(b1.child);
//			assertNull(b1.sub1);
//			assertNull(b1.sub2);
			//(foreign key relations should be set now)
			b1.refreshLinks();
			assertNotNull(b1.parent);
			assertEquals(b1bak.parent.id, b1.parent.id);
			assertTrue(b1bak == b1);
			if(b1bak.child != null) assertNotNull(b1.child);
			if(b1bak.child != null){
				assertEquals(b1bak.child.id, b1.child.id);
				assertTrue(b1bak.child == b1.child);
			}
			assertNotNull(b1.sub1);
			assertEquals(b1bak.sub1.length, b1.sub1.length);
			for(int i=0; i<b1.sub1.length; i++){
				assertEquals(b1bak.sub1[i].id, b1.sub1[i].id);
				assertTrue(b1bak.sub1[i] == b1.sub1[i]);
			}
			assertNotNull(b1.sub2);
			assertEquals(b1bak.sub2.id, b1.sub2.id);
			assert(b1bak.sub2 == b1.sub2);
			
			//--Cleanup
			d.disconnect();
		}
	}
	
	@Test
	public void testRefreshLinks(){
		for(Database d : eachType()){
			assertTrue(d.isConnected());
			//--Basic Setup
			TableFKBase parent = d.emptyObject(TableFKBase.class);
			TableFKRef1 child1 = d.emptyObject(TableFKRef1.class);
			TableFKRef1 child2 = d.emptyObject(TableFKRef1.class);
			parent.sub1 = new TableFKRef1[]{ child1, child2 };
			parent.deepFlush();
			parent = d.getObjectById(TableFKBase.class, parent.id);
			child1 = d.getObjectById(TableFKRef1.class, child1.id);
			child2 = d.getObjectById(TableFKRef1.class, child2.id);
			assertNotNull(parent);
			assertNotNull(child1);
			assertNotNull(child2);
			//--Basic Refresh
			parent.refreshLinks();
			assertNotNull(parent.sub1);
			assertEquals(2,parent.sub1.length);
			assertEquals(parent.sub1[0].id, child1.id);
			assertEquals(parent.sub1[1].id, child2.id);
			//--Strict Equality
			assertEquals(parent, parent.sub1[0].pid);
			assertEquals(parent, parent.sub1[1].pid);
		}
	}

}
