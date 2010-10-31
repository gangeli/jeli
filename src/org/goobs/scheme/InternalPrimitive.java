package org.goobs.scheme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedList;

import org.goobs.exec.ExitCode;
import org.goobs.exec.Log;


public class InternalPrimitive extends ScmObject implements Primitive{
	/**
	 *  
	 */
	private static final long serialVersionUID = 7056165862202703104L;
	
	protected static String ADD = "+";
	protected static String LESS_THAN = "<";
	protected static String GREATER_THAN = ">";
	protected static String SUBTRACT = "-";
	protected static String MULTIPLY = "*";
	protected static String DIVIDE = "/";
	protected static String EQARITH = "=";
	protected static String CAR = "car";
	protected static String CDR = "cdr";
	protected static String CONS = "cons";
	protected static String EQ = "eq?";
	protected static String EQUAL = "equal?";
	protected static String EVEN = "even?";
	protected static String EXIT = "exit";
	protected static String FLOOR = "floor";
	protected static String INPUT = "input";
	protected static String LOAD = "load";
	protected static String MAKEVECT = "make-vector";
	protected static String NOT = "not";
	protected static String NUMBER = "number?";
	protected static String PAIR = "pair?";
	protected static String PRINT = "print";
	protected static String PRINTF = "printf";
	protected static String PROCEDURE = "procedure?";
	protected static String SAME_FORM = "same-form?";
	protected static String SET_PARALLEL = "set-parallel!";
	protected static String SET_RESOLVE_BINDINGS = "set-resolve-bindings!";
	protected static String SLEEP = "sleep";
	protected static String SYMBOL = "symbol?";
	protected static String TYPE = "type";
	protected static String TOLIST = "to-list";
	protected static String VECTOR = "vector?";
	protected static String VECTREF = "vector-ref";
	protected static String VECTSET = "vector-set";

	
	private String id;
	private Scheme scm;
	
	protected InternalPrimitive(String i, Scheme s){
		super("#[subr " + i + "]", ScmObject.FUNCTION);
		id = i;
		scm = s;
	}
	
	protected static ScmObject makeSpecialForm(String nm){
		return new ScmObject("#[subr " + nm + "]", ScmObject.SPECIAL_FORM);
	}
	
	@Override
	public ScmObject apply(ScmObject args, SchemeThreadPool.SchemeThread thread) throws SchemeException{
		if(id.equals(InternalPrimitive.ADD)){
			return numToScmObject( add(args, 0) );
		}else if(id.equals(InternalPrimitive.SUBTRACT)){
			return numToScmObject( subtract(args, 0) );
		}else if(id.equals(InternalPrimitive.MULTIPLY)){
			return numToScmObject( multiply(args, 0) );
		}else if(id.equals(InternalPrimitive.DIVIDE)){
			return numToScmObject( divide(args, 0) );
		}else if(id.equals(InternalPrimitive.LESS_THAN)){
			return lessThan(args);
		}else if(id.equals(InternalPrimitive.GREATER_THAN)){
			return greaterThan(args);
		}else if(id.equals(InternalPrimitive.EQARITH)){
			return eqArith(args);
		}else if(id.equals(InternalPrimitive.CAR)){
			return car(args);
		}else if(id.equals(InternalPrimitive.CDR)){
			return cdr(args);
		}else if(id.equals(InternalPrimitive.CONS)){
			return cons(args);
		}else if(id.equals(InternalPrimitive.EQ)){
			return eqEval(args);
		}else if(id.equals(InternalPrimitive.EQUAL)){
			return equalEval(args);
		}else if(id.equals(InternalPrimitive.EVEN)){
			return evenEval(args);
		}else if(id.equals(InternalPrimitive.FLOOR)){
			return numToScmObject( floor(args) );
		}else if(id.equals(InternalPrimitive.INPUT)){
			return input(args);
		}else if(id.equals(InternalPrimitive.LOAD)){
			return load(args);
		}else if(id.equals(InternalPrimitive.MAKEVECT)){
			return makeVector(args);
		}else if(id.equals(InternalPrimitive.NOT)){
			return notEval(args);
		}else if(id.equals(InternalPrimitive.NUMBER)){
			return numberEval(args);
		}else if(id.equals(InternalPrimitive.PAIR)){
			return pairEval(args);
		}else if(id.equals(InternalPrimitive.PRINT)){
			return print(args);
		}else if(id.equals(InternalPrimitive.PRINTF)){
			return printf(args);
		}else if(id.equals(InternalPrimitive.PROCEDURE)){
			return procEval(args);
		}else if(id.equals(InternalPrimitive.SAME_FORM)){
			return sameForm(args);
		}else if(id.equals(InternalPrimitive.SET_RESOLVE_BINDINGS)){
			return setResolveBindings(args);
		}else if(id.equals(InternalPrimitive.SET_PARALLEL)){
			return setParallel(args, thread); 
		}else if(id.equals(InternalPrimitive.SLEEP)){
			return sleep(args);
		}else if(id.equals(InternalPrimitive.SYMBOL)){
			return symbolEval(args);
		}else if(id.equals(InternalPrimitive.TYPE)){
			return type(args);
		}else if(id.equals(InternalPrimitive.TOLIST)){
			return tolist(args);
		}else if(id.equals(InternalPrimitive.VECTOR)){
			return vectorEval(args);
		}else if(id.equals(InternalPrimitive.VECTREF)){
			return vectorGet(args);
		}else if(id.equals(InternalPrimitive.VECTSET)){
			return vectorSet(args);
		}else if(id.equals(InternalPrimitive.EXIT)){
			for(Runnable r : scm.getExitTasks()){
				r.run();
			}
			Log.exit(ExitCode.OK);
			return null;
		}else{
			throw new SchemeException("[INTERNAL]: cannot find primitive: " + id);
		}
	}
	
	private ScmObject numToScmObject(Object num){
		if(num instanceof Integer){
			return new ScmObject(num, ScmObject.INTEGER);
		}else if( num instanceof BigInteger){
			return new ScmObject(num, ScmObject.BIGINT);
		}else if( num instanceof Double){
			double n = ((Double) num).doubleValue();
			if(Math.floor(n) == n){
				return new ScmObject(new Integer((int) n), ScmObject.INTEGER);
			}else{
				return new ScmObject(num, ScmObject.DOUBLE);
			}
		}else if( num instanceof BigDecimal){
			return new ScmObject(num, ScmObject.BIGDEC);
		}else{
			throw new SchemeException("[INTERNAL]: not a valid number: " + num);
		}
	}
	
	//type:
	// 0 - int
	// 1 - big int
	// 2 - [float] not used!
	// 3 - double
	// 4 - big decimal
	private static Object add(ScmObject args, int type){
		//BASE CASE
		if(args.getType() == ScmObject.NIL){
			if(type == 0){
				return new Integer(0);
			}else if(type == 1){
				return BigInteger.ZERO;
			}else if(type == 3){
				return new Double(0.0);
			}else if(type == 4){
				return BigDecimal.ZERO;
			}else{
				throw new SchemeException("[INTERNAL]: add: invalid numeric base case");
			}
		}
		//get new 'most specific' type
		ScmObject first = args.car();
		if(first.isType(ScmObject.INTEGER)){
			//type is good as it is (no negative types)
		}else if(first.isType(ScmObject.BIGINT)){
			type = Math.max(type, 1);
		}else if(first.isType(ScmObject.DOUBLE)){
			type = Math.max(type, 3);
		}else if(first.isType(ScmObject.BIGDEC)){
			type = 4;
		}else{
			throw new SchemeException("add: non-numeric argument: " + first);
		}
		//RECURSIVE CASE
		Object rest = add(args.cdr(), type);
		if(rest instanceof Integer){
			Integer rtn =  ((Integer) first.getContent()) + ((Integer) rest);
			//overlflow check
			if((Integer) first.getContent() > 0 && (Integer) rest > 0 && rtn < 0){
					BigInteger f = new BigInteger( first.getContent().toString() );
					BigInteger s = new BigInteger( rest.toString() );
					return f.add(s);
			}
			//underflow check
			if((Integer) first.getContent() < 0 && (Integer) rest < 0 && rtn > 0){
					BigInteger f = new BigInteger( first.getContent().toString() );
					BigInteger s = new BigInteger( rest.toString() );
					return f.add(s);
			}
			return rtn;
		}else if(rest instanceof BigInteger){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigInteger) ){
				toAdd = new BigInteger(first.toString());
			}
			//no overflow possible
			return (BigInteger) ((BigInteger) toAdd).add( ((BigInteger) rest) );
		}else if(rest instanceof Double){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof Double) ){
				toAdd = Double.parseDouble(toAdd.toString());
			}
			Double rtn = (Double) ((Double) toAdd) + ((Double) rest);
			//precision loss check
			if( rtn / ((Double) rest) != (Double) toAdd){
				BigDecimal f = new BigDecimal(first.toString());
				BigDecimal r = new BigDecimal(rest.toString());
				return f.add(r);
			}
			return rtn;
		}else if(rest instanceof BigDecimal){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigDecimal) ){
				toAdd = new BigDecimal(first.toString());
			}
			//no precision loss possible
			return (BigDecimal) ((BigDecimal) toAdd).add( ((BigDecimal) rest) );
		}else{
			throw new SchemeException("[INTERNAL]: add: invalid numeric type");
		}
	}
	
	//type:
	// 0 - int
	// 1 - big int
	// 2 - [float] not used!
	// 3 - double
	// 4 - big decimal
	private static Object subtract(ScmObject args, int type){
		//if only one argument, take the negation of the value
		if( !args.isType(ScmObject.NIL) && args.cdr().isType(ScmObject.NIL)){
			//args = (0 . args);
			args = ScmObject.makePair(new ScmObject(new Integer(0), ScmObject.INTEGER), args);
		}
		return subtractHelper(args, type);
	}

	private static Object subtractHelper(ScmObject args, int type){
		//BASE CASE
		if(args.getType() == ScmObject.NIL){
			if(type == 0){
				return new Integer(0);
			}else if(type == 1){
				return BigInteger.ZERO;
			}else if(type == 3){
				return new Double(0.0);
			}else if(type == 4){
				return BigDecimal.ZERO;
			}else{
				throw new SchemeException("[INTERNAL]: subtract: invalid numeric base case");
			}
		}
		//get new 'most specific' type
		ScmObject first = args.car();
		if(first.isType(ScmObject.INTEGER)){
			//type is good as it is (no negative types)
		}else if(first.isType(ScmObject.BIGINT)){
			type = Math.max(type, 1);
		}else if(first.isType(ScmObject.DOUBLE)){
			type = Math.max(type, 3);
		}else if(first.isType(ScmObject.BIGDEC)){
			type = 4;
		}else{
			throw new SchemeException("subtract: non-numeric argument: " + first);
		}
		//RECURSIVE CASE
		Object rest = add(args.cdr(), type);
		if(rest instanceof Integer){
			Integer rtn =  ((Integer) first.getContent()) - ((Integer) rest);
			//overlflow check
			if((Integer) first.getContent() > 0 && (Integer) rest < 0 && rtn < 0){
					BigInteger f = new BigInteger( first.getContent().toString() );
					BigInteger s = new BigInteger( rest.toString() );
					return f.subtract(s);
			}
			//underflow check
			if((Integer) first.getContent() < 0 && (Integer) rest > 0 && rtn > 0){
					BigInteger f = new BigInteger( first.getContent().toString() );
					BigInteger s = new BigInteger( rest.toString() );
					return f.subtract(s);
			}
			return rtn;
		}else if(rest instanceof BigInteger){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigInteger) ){
				toAdd = new BigInteger(first.toString());
			}
			//no overflow possible
			return (BigInteger) ((BigInteger) toAdd).subtract( ((BigInteger) rest) );
		}else if(rest instanceof Double){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof Double) ){
				toAdd = Double.parseDouble(toAdd.toString());
			}
			Double rtn = (Double) ((Double) toAdd) - ((Double) rest);
			//precision loss check
			if( rtn / ((Double) rest) != (Double) toAdd){
				BigDecimal f = new BigDecimal(first.toString());
				BigDecimal r = new BigDecimal(rest.toString());
				return f.subtract(r);
			}
			return rtn;
		}else if(rest instanceof BigDecimal){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigDecimal) ){
				toAdd = new BigDecimal(first.toString());
			}
			//no precision loss possible
			return (BigDecimal) ((BigDecimal) toAdd).subtract( ((BigDecimal) rest) );
		}else{
			throw new SchemeException("[INTERNAL]: subtract: invalid numeric type");
		}
	}
	
	//type:
	// 0 - int
	// 1 - big int
	// 2 - [float] not used!
	// 3 - double
	// 4 - big decimal
	private static Object multiply(ScmObject args, int type){
		//BASE CASE
		if(args.getType() == ScmObject.NIL){
			if(type == 0){
				return new Integer(1);
			}else if(type == 1){
				return BigInteger.ONE;
			}else if(type == 3){
				return new Double(1.0);
			}else if(type == 4){
				return BigDecimal.ONE;
			}else{
				throw new SchemeException("[INTERNAL]: multiply: invalid numeric base case");
			}
		}
		//get new 'most specific' type
		ScmObject first = args.car();
		if(first.isType(ScmObject.INTEGER)){
			//type is good as it is (no negative types)
		}else if(first.isType(ScmObject.BIGINT)){
			type = Math.max(type, 1);
		}else if(first.isType(ScmObject.DOUBLE)){
			type = Math.max(type, 3);
		}else if(first.isType(ScmObject.BIGDEC)){
			type = 4;
		}else{
			throw new SchemeException("multiply: non-numeric argument: " + first);
		}
		//RECURSIVE CASE
		Object rest = multiply(args.cdr(), type);
		if(rest instanceof Integer){
			Integer rtn =  (Integer) ((Integer) first.getContent()) * ((Integer) rest);
			//overflow check
			if(rtn / ((Integer) rest) != (Integer) first.getContent()){
				BigInteger f = new BigInteger( first.getContent().toString() );
				BigInteger s = new BigInteger( rest.toString() );
				return f.multiply(s);
			}
			return rtn;
		}else if(rest instanceof BigInteger){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigInteger) ){
				toAdd = new BigInteger(first.toString());
			}
			//no overflow possible
			return (BigInteger) ((BigInteger) toAdd).multiply( ((BigInteger) rest) );
		}else if(rest instanceof Double){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof Double) ){
				toAdd = Double.parseDouble(toAdd.toString());
			}
			Double rtn = (Double) ((Double) toAdd) * ((Double) rest);
			if(rtn / ((Double) rest) != (Double) toAdd){
				BigDecimal f = new BigDecimal( first.getContent().toString() );
				BigDecimal s = new BigDecimal( rest.toString() );
				return f.multiply(s);
			}
			return rtn;
		}else if(rest instanceof BigDecimal){
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigDecimal) ){
				toAdd = new BigDecimal(first.toString());
			}
			//no overflow possible
			return (BigDecimal) ((BigDecimal) toAdd).multiply( ((BigDecimal) rest) );
		}else{
			throw new SchemeException("[INTERNAL]: multiply: invalid numeric type");
		}
	}

	private static Object divide(ScmObject args, int type){
		//if only one argument, take the negation of the value
		if( !args.isType(ScmObject.NIL) && args.cdr().isType(ScmObject.NIL)){
			//args = (1 . args);
			args = ScmObject.makePair(new ScmObject(new Integer(1), ScmObject.INTEGER), args);
		}
		return divideHelper(args, type);
	}

	private static Object divideHelper(ScmObject args, int type){
		//BASE CASE
		if(args.getType() == ScmObject.NIL){
			if(type == 0){
				return new Integer(1);
			}else if(type == 1){
				return BigInteger.ONE;
			}else if(type == 3){
				return new Double(1.0);
			}else if(type == 4){
				return BigDecimal.ONE;
			}else{
				throw new SchemeException("[INTERNAL]: divide: invalid numeric base case");
			}
		}
		//get new 'most specific' type
		ScmObject first = args.car();
		if(first.isType(ScmObject.INTEGER)){
			//type is good as it is (no negative types)
		}else if(first.isType(ScmObject.BIGINT)){
			type = Math.max(type, 1);
		}else if(first.isType(ScmObject.DOUBLE)){
			type = Math.max(type, 3);
		}else if(first.isType(ScmObject.BIGDEC)){
			type = 4;
		}else{
			throw new SchemeException("divide: non-numeric argument: " + first);
		}
		//RECURSIVE CASE
		Object rest = multiply(args.cdr(), type);
		if(rest instanceof Integer){
			if(rest.equals(0)){
				throw new SchemeException("divide by zero");
			}
			//Check if division results in integer or rational number
			if( ((Integer) first.getContent() % (Integer) rest) != 0){
				Double fd = new Double((Integer) first.getContent());
				Double sd = new Double((Integer) rest);
				//integer does not cast directly into a double
				if(	!fd.toString().equals(first.getContent().toString()) ||
					!sd.toString().equals(rest.toString())){
					BigDecimal f = new BigDecimal( first.getContent().toString() );
					BigDecimal s = new BigDecimal( rest.toString() );
					try {
						return f.divide(s);
					} catch (ArithmeticException e) {
						return fd/sd;	//IMPRECISION (non-terminating fraction)
					}
				}
				Double rtn = fd / sd;
				if(rtn / sd != fd){
					BigDecimal f = new BigDecimal( first.getContent().toString() );
					BigDecimal s = new BigDecimal( rest.toString() );
					try {
						return f.divide(s);
					} catch (ArithmeticException e) {
						return rtn;		//IMPRECISION (non-termination fraction)
					}
				}
				return rtn;
			}
			Integer rtn =  (Integer) ((Integer) first.getContent()) / ((Integer) rest);
			//overflow check: integer division cannot overflow
			return rtn;
		}else if(rest instanceof BigInteger){
			if(rest.equals(BigInteger.ZERO)){
				throw new SchemeException("divide by zero");
			}
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigInteger) ){
				toAdd = new BigInteger(first.toString());
			}
			//Check if division results in integer or rational number
			if( ((BigInteger) first.getContent()).mod((BigInteger) rest) != BigInteger.ZERO ){
				Double fd = Double.parseDouble(first.getContent().toString());
				Double sd = Double.parseDouble(rest.toString());
				//integer does not cast directly into a double
				if(	!fd.toString().equals(first.getContent().toString()) ||
					!sd.toString().equals(rest.toString())){
					BigDecimal f = new BigDecimal( first.getContent().toString() );
					BigDecimal s = new BigDecimal( rest.toString() );
					System.out.println(f + " / " + s);
					try {
						return f.divide(s);
					} catch (ArithmeticException e) {
						return fd/sd;	//IMPRECISION (non-terminating fraction)
					}
				}
				Double rtn = fd / sd;
				if(rtn / sd != fd){
					BigDecimal f = new BigDecimal( first.getContent().toString() );
					BigDecimal s = new BigDecimal( rest.toString() );
					try {
						return f.divide(s);
					} catch (ArithmeticException e) {
						return fd/sd;	//IMPRECISION (non-terminating fraction)
					}
				}
				return rtn;
			}
			//no overflow possible
			try {
				return (BigInteger) ((BigInteger) toAdd).divide( ((BigInteger) rest) );
			} catch (ArithmeticException e) {
				return Double.parseDouble(toAdd.toString())/Double.parseDouble(rest.toString());	//IMPRECISION
			}
		}else if(rest instanceof Double){
			if(rest.equals(0)){
				throw new SchemeException("divide by zero");
			}
			Object toAdd = first.getContent();
			if( !(toAdd instanceof Double) ){
				toAdd = Double.parseDouble(toAdd.toString());
			}
			Double rtn = (Double) ((Double) toAdd) / ((Double) rest);
			if(rtn / ((Double) rest) != (Double) toAdd){
				BigDecimal f = new BigDecimal( first.getContent().toString() );
				BigDecimal s = new BigDecimal( rest.toString() );
				try {
					return f.divide(s);
				} catch (ArithmeticException e) {
					return rtn;	//IMPRECISION (non-terminating fraction)
				}
			}
			return rtn;
		}else if(rest instanceof BigDecimal){
			if(rest.equals(BigDecimal.ZERO)){
				throw new SchemeException("divide by zero");
			}
			Object toAdd = first.getContent();
			if( !(toAdd instanceof BigDecimal) ){
				toAdd = new BigDecimal(first.toString());
			}
			//no overflow possible
			try {
				return (BigInteger) ((BigInteger) toAdd).divide( ((BigInteger) rest) );
			} catch (ArithmeticException e) {
				return Double.parseDouble(toAdd.toString())/Double.parseDouble(rest.toString());	//IMPRECISION (non-terminating fraction)
			}
		}else{
			throw new SchemeException("[INTERNAL]: divide: invalid numeric type");
		}
	}
	
	private static ScmObject lessThan(ScmObject args){
		if(args.cdr().isNil() || !args.cdr().cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		ScmObject a = args.car();
		ScmObject b = args.cdr().car();
		int type = Math.max(a.getType(), b.getType());
		try {
			a.cast(type);
			b.cast(type);
		} catch (SchemeException e) {
			throw new SchemeException("non-numeric arguments");
		}
		boolean rtn = false;
		if(type == ScmObject.INTEGER){
			rtn = (Integer) a.getContent() < (Integer) b.getContent();
		}else if(type == ScmObject.BIGINT){
			rtn = ((BigInteger) a.getContent()).compareTo( (BigInteger) b.getContent() ) < 0;
		}else if(type == ScmObject.DOUBLE){
			rtn = (Double) a.getContent() < (Double) b.getContent();
		}else if(type == ScmObject.BIGDEC){
			rtn = ((BigDecimal) a.getContent()).compareTo( (BigDecimal) b.getContent() ) < 0;
		}else{
			throw new SchemeException("[INTERNAL]: non-numeric argument should have been caught in ScmObject.cast()");
		}
		if(!rtn){
			return ScmObject.makeFalse();
		}else{
			return ScmObject.makeTrue();
		}
	}
	
	private static ScmObject greaterThan(ScmObject args){
		if(args.cdr().isNil() || !args.cdr().cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		ScmObject a = args.car();
		ScmObject b = args.cdr().car();
		int type = Math.max(a.getType(), b.getType());
		try {
			a.cast(type);
			b.cast(type);
		} catch (SchemeException e) {
			throw new SchemeException("non-numeric arguments");
		}
		boolean rtn = false;
		if(type == ScmObject.INTEGER){
			rtn = (Integer) a.getContent() > (Integer) b.getContent();
		}else if(type == ScmObject.BIGINT){
			rtn = ((BigInteger) a.getContent()).compareTo( (BigInteger) b.getContent() ) > 0;
		}else if(type == ScmObject.DOUBLE){
			rtn = (Double) a.getContent() > (Double) b.getContent();
		}else if(type == ScmObject.BIGDEC){
			rtn = ((BigDecimal) a.getContent()).compareTo( (BigDecimal) b.getContent() ) > 0;
		}else{
			throw new SchemeException("[INTERNAL]: non-numeric argument should have been caught in ScmObject.cast()");
		}
		if(!rtn){
			return ScmObject.makeFalse();
		}else{
			return ScmObject.makeTrue();
		}
	}
	
	private static ScmObject eqArith(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}
		if (!args.car().eqeq(args.cdr().car())) {
			return ScmObject.makeFalse();
		} else {
			return ScmObject.makeTrue();
		}
	}
	
	private static ScmObject car(ScmObject args){
		if(!args.cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().getType() != ScmObject.PAIR){
			System.out.println(args);
			throw new SchemeException("argument is not a pair");
		}
		return args.car().car();
	}
	
	private static ScmObject cdr(ScmObject args){
		if(!args.cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().getType() != ScmObject.PAIR){
			throw new SchemeException("argument is not a pair");
		}
		return args.car().cdr();
	}
	
	private static ScmObject cons(ScmObject args){
		if(args.isNil() || 
				args.cdr().isNil() ||
				!args.cdr().cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		return ScmObject.makePair(args.car(), args.cdr().car());
	}
	
	private ScmObject load(ScmObject args){
		if(args.isNil() ||
		  !args.cdr().isNil()){
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().getType() != ScmObject.STRING){
			throw new SchemeException("non-string filename");
		}
		String fileName = (String) args.car().getContent();
		scm.loadExternalLib(fileName, false);
		return new ScmObject("okay", ScmObject.WORD);
	}
	
	private ScmObject eqEval(ScmObject args) {
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}
		if (!args.car().eq(args.cdr().car())) {
			return ScmObject.makeFalse();
		} else {
			return ScmObject.makeTrue();
		}
	}
	
	private ScmObject equalEval(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}
		if( !args.car().equals(args.cdr().car())){
			return ScmObject.makeFalse();
		}else{
			return ScmObject.makeTrue();
		}
	}
	
	private ScmObject evenEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}else if( !args.car().isType(ScmObject.INTEGER) ){
			throw new SchemeException("not an integer argument");
		}
		Double num = new Double( (Integer) args.car().getContent() );
		if(Math.floor(num/2) == num/2){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private Double floor(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject num = args.car();
		Double n = new Double(0);
		if(num.isType(ScmObject.INTEGER)){
			n = new Double( (Integer) args.car().getContent() );
		}else if(num.isType(ScmObject.DOUBLE)){
			n = new Double( ((Double) num.getContent()).intValue() );
		}else if(num.isType(ScmObject.BIGINT)){
			n = Math.floor( ((BigInteger) args.car().getContent()).doubleValue() );
		}else if(num.isType(ScmObject.BIGDEC)){
			n = Math.floor( ((BigDecimal) args.car().getContent()).doubleValue() );
		}else{
			throw new SchemeException("bad type to floor");
		}
		return Math.floor(n);
	}
	
	private ScmObject input(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject prompt = args.car();
		if(!prompt.isType(ScmObject.STRING)){
			throw new SchemeException("input takes a string argument");
		}
		System.out.print((String) prompt.getContent());
		System.out.print(" ");
		//read
		BufferedReader reader = new BufferedReader (new InputStreamReader (System.in));
		try {
			return new ScmObject(reader.readLine(), ScmObject.STRING);
		} catch (IOException e) {
			throw new SchemeException("could not access keyboard");
		}
	}
	
	private ScmObject makeVector(ScmObject args){
		LinkedList <ScmObject> contents = vectHelper(args, new LinkedList <ScmObject> ());
		ScmObject[] array = new ScmObject[contents.size()];
		int i=0;
		Iterator <ScmObject> iter = contents.iterator();
		while(iter.hasNext()){
			array[i] = iter.next();
			i++;
		}
		return new ScmObject(array, ScmObject.VECTOR);
	}
	private LinkedList <ScmObject> vectHelper(ScmObject args, LinkedList <ScmObject> base){
		if(args.isType(ScmObject.NIL)){
			return base;
		}else{
			base.add(args.car());
			return vectHelper(args.cdr(), base);
		}
	}
	
	private ScmObject notEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.FALSE)){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject numberEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.INTEGER) 	||
				args.car().isType(ScmObject.BIGINT) ||
				args.car().isType(ScmObject.DOUBLE) ||
				args.car().isType(ScmObject.BIGDEC)		){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject pairEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.PAIR)){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject print(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.STRING)){
			//don't want quotes
			System.out.println(args.car().getContent());
		}else{
			System.out.println(args.car());
		}
		return new ScmObject("ok", ScmObject.WORD);
	}
	
	private ScmObject printf(ScmObject args){
		//--Overhead
		if (args.isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(!args.car().isType(ScmObject.STRING)){
			throw new SchemeException("first argument must be a string");
		}
		//don't want quotes
		char[] template = args.car().getContent().toString().toCharArray();
		args = args.cdr();
		
		//--Fill the string
		StringBuilder toPrint = new StringBuilder();
		for(int i=0; i<template.length; i++){
			if(template[i] == '%'){
				//(get the object to fill it with)
				if(args.isNil()){
					throw new SchemeException("too few values");
				}
				ScmObject obj = args.car();
				args = args.cdr();
				//(fill according to the type)
				i+=1;
				char type = template[i];
				switch(type){
				case 's':
				case 'S':
					if(!obj.isType(ScmObject.STRING) && !obj.isType(ScmObject.WORD)){
						throw new SchemeException("not a string: " + obj);
					}
					toPrint.append(obj.getContent().toString());
					break;
				case 'd':
				case 'D':
				case 'i':
				case 'I':
					if(!obj.isType(ScmObject.INTEGER) && !obj.isType(ScmObject.BIGINT)){
						throw new SchemeException("not an integer: " + obj);
					}
					toPrint.append(obj.getContent().toString());
					break;
				case 'f':
				case 'F':
					if(!obj.isType(ScmObject.DOUBLE) && !obj.isType(ScmObject.BIGDEC)
							&& !obj.isType(ScmObject.INTEGER) && !obj.isType(ScmObject.BIGINT)){
						throw new SchemeException("not a decimal: " + obj);
					}
					toPrint.append(obj.getContent());
					break;
				default:
					throw new SchemeException("Not a valid flag: " + type);
				}
			}else{
				toPrint.append(template[i]);
			}
		}
		
		//--Print the string
		if(!args.isNil()){
			throw new SchemeException("too many values");
		}
		System.out.println(toPrint);
		
		return new ScmObject("ok", ScmObject.WORD);
	}
	
	private ScmObject procEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if( args.car().isType(ScmObject.FUNCTION) ){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject sameForm(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject arg1 = args.car();
		ScmObject arg2 = args.cdr().car();
		if(arg1.sameFormAs(arg2)){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject setParallel(ScmObject args, SchemeThreadPool.SchemeThread thread){
		if (args.isNil() || (!args.cdr().isNil() && !args.cdr().cdr().isNil()) ) {
			throw new SchemeException("bad number of parameters");
		}else{
			if(thread != null && thread.tid != SchemeThreadPool.ROOT_THREAD){
				throw new SchemeException("Cannot change parallelization state outside of root thread");
			}
			if(args.car().isFalse()){
				Scheme.auxPool = null;
			}else{
				int numThreads = 10;
				if(!args.cdr().isNil()){
					if(args.cdr().car().isType(ScmObject.INTEGER)){
						numThreads = (Integer) args.cdr().car().getContent();
					}else{
						throw new SchemeException("Second argument to " + SET_PARALLEL + " must be an integer");
					}
				}
				if(numThreads <= 0){
					throw new SchemeException("Non-positive thread count");
				}
				Scheme.auxPool = new SchemeThreadPool(numThreads);
			}
		}
		return new ScmObject("ok", ScmObject.WORD);
	}
	
	private ScmObject setResolveBindings(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}else{
			if(args.car().isFalse()){
				Environment.setResolveBindings(false);
			}else{
				Environment.setResolveBindings(true);
			}
		}
		return new ScmObject("ok", ScmObject.WORD);
	}
	
	private ScmObject sleep(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}else if(! args.car().isType(ScmObject.INTEGER)){
			throw new SchemeException("non-integer sleep time");
		}
		try {
			Thread.sleep((Integer)args.car().getContent());
		} catch (InterruptedException e) {} 
		return new ScmObject("done", ScmObject.WORD);
	}
	
	private ScmObject symbolEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.BINDING)){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	private ScmObject type(ScmObject args){
		if(args.isNil() || !args.cdr().isNil() ){
			throw new SchemeException("bad number of parameters");
		}
		ScmObject obj = args.car();
		switch(obj.getType()){
		case ScmObject.BIGDEC:
			return new ScmObject("Decimal (big)", ScmObject.STRING);
		case ScmObject.BIGINT:
			return new ScmObject("Integer (big)", ScmObject.STRING);
		case ScmObject.BINDING:
			return new ScmObject("Binding", ScmObject.STRING);
		case ScmObject.DOUBLE:
			return new ScmObject("Decimal (double)", ScmObject.STRING);
		case ScmObject.FALSE:
			return new ScmObject("False Type", ScmObject.STRING);
		case ScmObject.FUNCTION:
			return new ScmObject("Function (lamba expression)", ScmObject.STRING);
		case ScmObject.INTEGER:
			return new ScmObject("Integer", ScmObject.STRING);
		case ScmObject.NIL:
			return new ScmObject("Nil Type", ScmObject.STRING);
		case ScmObject.PAIR:
			return new ScmObject("Pair", ScmObject.STRING);
		case ScmObject.SPECIAL_FORM:
			return new ScmObject("Function (special form)", ScmObject.STRING);
		case ScmObject.STRING:
			return new ScmObject("String", ScmObject.STRING);
		case ScmObject.TRUE:
			return new ScmObject("True Type", ScmObject.STRING);
		case ScmObject.VECTOR:
			return new ScmObject("Vector", ScmObject.STRING);
		case ScmObject.WORD:
			return new ScmObject("Word", ScmObject.STRING);
		default:
			throw new SchemeException("INTERNAL: Unknown object type: " + obj.getType());
		}
	}
	
	private ScmObject tolist(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		return args.car().toList();
	}
	
	private ScmObject vectorEval(ScmObject args){
		if (args.isNil() || !args.cdr().isNil() ) {
			throw new SchemeException("bad number of parameters");
		}
		if(args.car().isType(ScmObject.VECTOR)){
			return ScmObject.makeTrue();
		}else{
			return ScmObject.makeFalse();
		}
	}
	
	//(vector-get vector index)
	private ScmObject vectorGet(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}else if( !args.car().isType(ScmObject.VECTOR)){
			throw new SchemeException("not a vector: " + args.car());
		}else if( !args.cdr().car().isType(ScmObject.INTEGER)){  //cannot be a big integer
			throw new SchemeException("non-integer index: " + args.cdr().car());
		}else{
			try {
				return ((ScmObject[]) args.car().getContent())[(Integer) args.cdr().car().getContent()];
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new SchemeException("index out of bounds: " +  args.cdr().car() );
			}
		}
	}
	
	//(vector-set vector index toSet)
	private ScmObject vectorSet(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || args.cdr().cdr().isNil() || !args.cdr().cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}else if( !args.car().isType(ScmObject.VECTOR)){
			throw new SchemeException("not a vector: " + args.car());
		}else if( !args.cdr().car().isType(ScmObject.INTEGER)){  //cannot be a big integer
			throw new SchemeException("non-integer index: " + args.cdr().car());
		}else{
			ScmObject[] content = ((ScmObject[]) args.car().getContent());
			try {
				content[(Integer) args.cdr().car().getContent()] = args.cdr().cdr().car();
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new SchemeException("index out of bounds: " +  args.cdr().car() );
			}
			return new ScmObject("ok", ScmObject.WORD);
		}
	}
}
