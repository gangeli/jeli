package org.goobs.scheme;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class ScmObject implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3876481757811411253L;
	
	public static final int NIL = 0;
	public static final int BINDING = 1;
	public static final int FALSE = 3;
	public static final int TRUE = 4;
	
	//agreement - larger type numbers = more precision
	//furthermore, a return will always be of the most accurate
	//   aka most specific type possible.
	public static final int INTEGER = 10;
	public static final int BIGINT = 11;
	public static final int DOUBLE = 13;
	public static final int BIGDEC = 14;
	//---
	
	public static final int FUNCTION = 20;
	public static final int SPECIAL_FORM = 21;
	
	public static final int WORD = 30;
	public static final int STRING = 31;
	public static final int PAIR = 40;
	public static final int VECTOR = 41;
	
	private static final int END_PAREN = -11;
	
	
	private Object content;
	private int type;
	
	
	
	private class Lambda{
		private ScmObject vars;
		private ScmObject body;
		private Environment creationEnvironment;
		
		private Lambda(ScmObject args, ScmObject bod, Environment env){
			vars = args;
			body = bod;
			creationEnvironment = env;
		}
		
		private ScmObject apply(ScmObject vals, SchemeThreadPool.SchemeThread thread){
			ScmObject apply_vars = vars;
			ScmObject apply_body = body;
			Environment env = new Environment(creationEnvironment);;
			Environment runEnv = null;
			if(thread != null && thread.tid != SchemeThreadPool.ROOT_THREAD){
				runEnv = env.extendLockedEnvironment();
			}else{
				runEnv = env;
			}
			//initialize the bindings in the new environment
			while(vals.type != NIL){
				if(apply_vars.type == NIL){
					throw new SchemeException("lambda: too many arguments to lambda");
				}
				ScmObject binding;
				ScmObject value;

				if(apply_vars.car().equals(ScmObject.specify("."))){
					if(apply_vars.cdr().isType(ScmObject.NIL) ||
						!	apply_vars.cdr().cdr().isType(ScmObject.NIL)){
						throw new SchemeException("lambda: attempted evaluation of ill-defined lambda");
					}
					//list lambda
					binding = apply_vars.cdr().car();
					value = vals;
					if(binding.type == BINDING){
						env.putBinding(binding, value);
					}
					if(!apply_vars.cdr().cdr().isNil()){
						throw new SchemeException("lambda: too many variables in declaration");
					}
					apply_vars = apply_vars.cdr().cdr();
					break; //this must be the last argument
				}else{
					//standard lambda
					binding = apply_vars.car();
					value = vals.car();
					if(binding.type == BINDING){
						env.putBinding(binding, value);
					}
					vals = vals.cdr();

					if(apply_vars.type == NIL){
						throw new SchemeException("lambda: too many arguments to lambda");
					}
					apply_vars = apply_vars.cdr();
				}
			}
			// too few arguments
			if(apply_vars.type != NIL){
				throw new SchemeException("Too few arguments to lambda");
			}

			// list lambda without a list term
			if (apply_vars.isType(ScmObject.PAIR) && 
					apply_vars.car().equals(ScmObject.specify("."))) {
				if (apply_vars.cdr().isType(ScmObject.NIL)
						|| !apply_vars.cdr().cdr().isType(ScmObject.NIL)) {
					throw new SchemeException(
							"lambda: attempted evaluation of ill-defined lambda");
				}
				env.putBinding(apply_vars.cdr().car(), ScmObject.makeNil());
			}
			
			//apply the body of the lambda
			ScmObject rtn = makeNil();
			while(apply_body.type != NIL){
				rtn = runEnv.eval(apply_body.car(), thread);
				apply_body = (apply_body.cdr());
			}
			return rtn;
		}
		
		private ScmObject toList(ScmObject vals){
			ScmObject apply_body = body.car();
			if(!body.cdr().isNil()){
				throw new SchemeException("toList cannot handle multiple term lambda bodies yet");
			}
			Environment childEnv = new Environment(creationEnvironment);
			ScmObject apply_vars = vars;
			while(!vals.isNil()){
				if(apply_vars.isNil()){
					throw new SchemeException("Invalid number of arguments to function");
				}
				ScmObject valList = vals.car();
				childEnv.putBinding(apply_vars.car(), valList);
				apply_vars = apply_vars.cdr();
				vals = vals.cdr();
			}
			ScmObject rtn = apply_body.toList(childEnv);
			return rtn;			
		}
		
		@Override
		public String toString(){
			int memory = new Integer(System.identityHashCode(this));
			return "#[closure arglist=" + vars + " 0x" + Integer.toHexString(memory) + "]";
		}
	}
	
	//Initialization
	public ScmObject(Object c, int t){
		content = c;
		type = t;
	}
	
	public static ScmObject specify(String obj){
		LinkedList <String> exp = read(obj);
		try {
			return specify(exp.iterator());
		} catch (SchemeException e) {
			throw new SchemeException("invalid Scheme object: " + obj);
		}
	}
	private static ScmObject specify(Iterator <String> iter)throws SchemeException{
		String term;
		while(iter.hasNext()){
			term = iter.next();
			Object content;
			int type;
			//LITERAL EXPRESSIONS
			//  numbers
			try {
				content = Integer.parseInt(term);
				type = ScmObject.INTEGER;
				return new ScmObject(content, type);
			} catch (NumberFormatException e) {}
			try{
				content = new BigInteger(term);
				type = ScmObject.BIGINT;
				return new ScmObject(content, type);
			} catch (NumberFormatException e) {}
			try {
				// try to fit it into a double
				content = Double.parseDouble(term);
				if (!content.toString().equals(term)) {
					// fine, be that way. BigDecimal it is
					content = new BigDecimal(term);
					type = ScmObject.BIGDEC;
					return new ScmObject(content, type);
				}
				type = ScmObject.DOUBLE;
				return new ScmObject(content, type);
			} catch (NumberFormatException e) {}
			//  other
			if(term.equals("()")){
				return makeNil();
			}else if(term.equals("#f")){
				return makeFalse();
			}else if(term.equals("#t")){
				return makeTrue();
			}
			
			//COMPLEX EXPRESSIONS
			//lists
			if(term.equals("(")){
				ScmObject[] cont = new ScmObject[2];
				cont[0] = specify(iter);	//will be at least one element guaranteed
				cont[1] = makeList(iter);
				return new ScmObject(cont, ScmObject.PAIR);
			}else if(term.equals(")")){
				return new ScmObject(")", ScmObject.END_PAREN);
			}
			//strings
			if(term.equals("\"")){
				ScmObject rtn = new ScmObject("", STRING);
				String temp = iter.next();
				while(temp != "\""){
					rtn.content = rtn.content + temp + " ";
					temp = iter.next();
				}
				rtn.content =  ((String) rtn.content).trim();
				return rtn;
			}
			
			//BINDING
			//(by process of elimination)
			return new ScmObject(term, ScmObject.BINDING);
		}
		throw new SchemeException();
	}
	
	private static ScmObject makeList(Iterator <String> iter){
		ScmObject[] content = new ScmObject[2];
		content[0] = specify(iter);
		if(content[0].type == ScmObject.END_PAREN){
			return makeNil();
		}else{
			content[1] = makeList(iter);
			return new ScmObject(content, ScmObject.PAIR);
		}
	}
	
	private static LinkedList <String> read(String exp){
		LinkedList <String> rtn = new LinkedList <String> ();
		StringTokenizer tokens = new StringTokenizer(exp);
		inQuotes = false;
		//iterate through the terms of the expression
		while(tokens.hasMoreElements()){
			String term = tokens.nextToken();
			add(rtn, term);
		}
		
		return rtn;
	}
	
	private static boolean inQuotes;
	private static void add(LinkedList <String> list, String term){
		if(term.trim().equals("")){
			return;
		}else if(term.charAt(0) == '(' || term.charAt(0) == '['){
			if(!inQuotes){
				//check for double parenthesis as nil
				if(term.length() > 1 && (term.charAt(1) == ')' || term.charAt(1) == ']')){
					list.add("()");
				}else{
					list.add("(");
					add(list, term.substring(1));
				}
			}else{
				list.add(term);
			}
		}else if(term.charAt(term.length()-1) == ')' || term.charAt(term.length()-1) == ']'){
			if(!inQuotes){
				add(list, term.substring(0, term.length()-1));
				list.add(")");
			}else{
				list.add(term);
			}
		}else if(term.charAt(0) =='\"'){
			inQuotes = !inQuotes;
			list.add("\"");
			add(list, term.substring(1));
		}else if(term.charAt(term.length()-1) == '\"'){
			add(list, term.substring(0, term.length()-1));
			list.add("\"");
			inQuotes = !inQuotes;
		}else if(term.charAt(0) == '\''){
			list.add("(");
			list.add("quote");
			add(list, term.substring(1));
			list.add(")");
		}else{
			list.add(term);
		}
	}
	
	
	
	
	private final static ScmObject nil = new ScmObject("()", ScmObject.NIL);
	public static ScmObject makeNil(){
		return nil;
	}
	
	public static ScmObject makeFalse(){
		return new ScmObject("#f", ScmObject.FALSE);
	}
	
	public static ScmObject makeTrue(){
		return new ScmObject("#t", TRUE);
	}
	
	public static ScmObject makePair(ScmObject car, ScmObject cdr){
		ScmObject[] content = {car, cdr};
		return new ScmObject(content, ScmObject.PAIR);
	}
	
	public static ScmObject makeLambda(ScmObject args, Environment env){
		if(args.type != PAIR || args.cdr().type != PAIR){
			throw new SchemeException("lambda: cannot create lambda");
		}
		ScmObject rtn = new ScmObject( null, FUNCTION);
		rtn.content = rtn.new Lambda(args.car(), args.cdr(), env);
		return rtn;
	}
	
	public ScmObject apply(ScmObject args, SchemeThreadPool.SchemeThread thread){
		if(type != FUNCTION){
			if(type == BINDING){
				return ScmObject.makePair(this, args);
			}else{
				throw new SchemeException("apply: bad function: " + this);
			}
		}else{
			return ((Lambda) content).apply(args, thread);
		}
	}
	
	public ScmObject car(){
		if(type != PAIR){
			throw new SchemeException("car: not a valid pair: " + this);
		}else{
			return ((ScmObject[]) content)[0];
		}
	}
	
	public ScmObject cdr(){
		if(type != PAIR){
			throw new SchemeException("cdr: not a valid pair: " + this);
		}else{
			return ((ScmObject[]) content)[1];
		}
	}
	
	public void replaceCar(ScmObject arg){
		if(type != PAIR){
			throw new SchemeException("set-car!: not a valid pair");
		}
		((ScmObject[]) content)[0] = arg;
	}
	
	public void replaceCdr(ScmObject arg){
		if(type != PAIR){
			throw new SchemeException("set-cdr!: not a valid pair");
		}
		((ScmObject[]) content)[1] = arg;
	}
	
	public boolean eq(ScmObject obj){
		if( (type == INTEGER && obj.type == INTEGER) ||
		    (type == DOUBLE && obj.type == DOUBLE)){
			return content == obj.content;
		}else if( ( type == BIGINT && obj.type == BIGINT) ||
				  ( type == BIGDEC && obj.type == BIGDEC) ){
			return content.equals(obj.content);
		}else{
			return this == obj;
		}
	}
	
	public boolean eqeq(ScmObject obj){
		if( (type == INTEGER && obj.type == INTEGER) ||
		    (type == DOUBLE && obj.type == DOUBLE)){
			return content == obj.content;
		}else if( ( type == BIGINT && obj.type == BIGINT) ||
				  ( type == BIGDEC && obj.type == BIGDEC) ){
			return content.equals(obj.content);
		}else{
			throw new SchemeException("non-numeric arguments");
		}
	}
	
	public boolean eqList(ScmObject obj){
		if(obj.type == this.type){
			if(this.type == PAIR){
				return this.car().eqList(obj.car()) && this.cdr().eqList(obj.cdr());
			}else{
				return this.content.equals(obj.content);
			}
		}else{
			return false;
		}
	}
	
	private static void getClauses(ScmObject a, List <ScmObject> clauses){
		if(a.isNil()){
			return;
		}
		if(a.car().isType(PAIR) && a.car().car().content.equals("and")){
			ScmObject rec = a.car().cdr();
			getClauses(rec, clauses);
		}else{
			clauses.add(a.car());
		}
		getClauses(a.cdr(), clauses);
	}
	
	private static boolean sameFormAnd(ScmObject a, ScmObject b){
		//--Get the clauses for both ands
		List <ScmObject> clausesA = new ArrayList <ScmObject> ();
		List <ScmObject> clausesB = new ArrayList <ScmObject> ();
		getClauses(a, clausesA);
		getClauses(b, clausesB);
		
		//--Compare the clauses
		for(ScmObject termA : clausesA){
			boolean good = false;
			for(ScmObject termB : clausesB){
				if(termA.sameFormAs(termB)){
					good = true;
					break;
				}
			}
			if(!good){
				return false;
			}
		}
		return true;
	}
	
	public boolean sameFormAs(ScmObject obj){
		if(obj == null){
			return false;
		}
		switch(obj.type){
		case BINDING:
			String a = this.getContent().toString();
			String b = obj.getContent().toString();
			if(a.length() > 1 && b.length() > 1){
				return a.equals(b);
			}else{
				return a.length() == b.length();
			}
		case FUNCTION:
		case SPECIAL_FORM:
			return this.toList().sameFormAs(obj.toList());
		case WORD:
		case STRING:
			return this.content.equals(obj.content);
		case INTEGER:
		case BIGINT:
		case DOUBLE:
		case BIGDEC:
			return this.content.equals(obj.content);
		case PAIR:
			if(!this.isType(PAIR)){
				return false;
			}
			if(car().content.equals("and") && obj.car().content.equals("and")){
				return sameFormAnd(cdr(), obj.cdr());
			}
			return car().sameFormAs(obj.car())
					&& cdr().sameFormAs(obj.cdr());
		case VECTOR:
			return false;
//			throw new SchemeException("sameFormAs not yet defined for vectors");
		default:
			return obj.isType(type);
		}
	}
	
	public void cast(int t) throws SchemeException{
		if(t == this.type){
			return;
		}else if(t == BIGDEC){
			if(type == INTEGER){
				content = new BigDecimal((Integer) content);
			}else if(type == BIGINT){
				content = new BigDecimal((BigInteger) content);
			}else if(type == DOUBLE){
				content = new BigDecimal((Double) content);
			}else{
				throw new SchemeException("[INTERNAL]: invalid type cast: " + type + " to " + t);
			}
		}else if(t == DOUBLE){
			if(type == INTEGER){
				content = new Double((Integer) content);
			}else if(type == BIGINT){
				content = Double.parseDouble(content.toString());
			}else{
				throw new SchemeException("[INTERNAL]: invalid type cast: " + type + " to " + t);
			}
		}else if(t == BIGINT){
			if(type == INTEGER){
				content = new BigInteger(content.toString());
			}else{
				throw new SchemeException("[INTERNAL]: invalid type cast: " + type + " to " + t);
			}
		}else{
			throw new SchemeException("[INTERNAL]: invalid type cast: " + type + " to " + t);
		}
		type = t;
	}	
	
	public void setTo(ScmObject newObj){
		if(this.type == NIL){ throw new IllegalStateException("Cannot set! nil"); }
		content = newObj.content;
		type = newObj.type;
	}

	public Object getContent(){
		return content;
	}
	
	public int getType(){
		return type;
	}
	
	public boolean isType(int type){
		return this.type == type;
	}
	
	public boolean isNil(){
		return type == NIL;
	}
	
	public boolean isFalse(){
		return type == FALSE;
	}
	
	public ScmObject toList(){
		return toList(null);
	}
	
	private ScmObject toList(Environment env){
		switch(this.type){
		case STRING:
		case WORD:
		case NIL:
			//--Base Case
			return this;
		case BINDING:
			//--Bindings are attempted to be resolved
			if(env != null){
				ScmObject val = env.getNonglobalBinding(this);
				if(val != null){
					return val.toList();
				}
			}
			return this;
		case SPECIAL_FORM:
		case FUNCTION:
			//--Functions
			if(! (this.content instanceof Lambda) ){
				throw new SchemeException("cannot convert functions to list");
			}else{
				//(re-create a lambda expression)
				Lambda lamb = (Lambda) this.content;
				ScmObject rtn = ScmObject.makePair(lamb.vars.toList(null), lamb.body.toList(lamb.creationEnvironment));
				rtn = ScmObject.makePair(new ScmObject("lambda", ScmObject.BINDING), rtn);
				return rtn;
			}
		case PAIR:
			//--Pairs
			//(lambda)
			//more specifically, the first term is a binding to a lambda
			//expression: (f a b c ...), where f:=(lambda (x y z ...) ...)
			if(env != null && this.car().isType(BINDING)){
				ScmObject val = null;
				val = env.getNonglobalBinding(this.car());
				if(val != null && (val.content instanceof Lambda)){
					//(all the conditions have been satisfied)
					Lambda lamb = (Lambda) val.content;
					return lamb.toList(this.cdr().toList(env));
				}
			}
			
			//(other pair)
			return ScmObject.makePair(this.car().toList(env), this.cdr().toList(env));
		default:
			return new ScmObject(this.content.toString(), ScmObject.WORD);
		}
	}
	
	@Override
	public ScmObject clone(){
		return new ScmObject(content, type);
	}
	
	@Override
	public String toString(){
		//pairs
		if( type == PAIR ){
			return pairToString();
		//strings
		}else if( type == STRING ){
			return "\"" + (String) content + "\"";
		//vectors
		}else if( type == VECTOR ){
			return vectToString();
		}else if( type == NIL ){
			return "()";
		}else{
			return content.toString();
		}
	}
	
	@Override
	public int hashCode(){
		return content.hashCode();
	}
	
	@Override
	public boolean equals(Object o){
		if(!(o instanceof ScmObject)){
			return false;
		}else{
			ScmObject obj = (ScmObject) o;
			//boolean equality
			if( (type == TRUE && obj.type == TRUE) ||
				(type == FALSE && obj.type == FALSE)){
				return true;
			}
			return content.equals(obj.content) && type == obj.type;
		}
	}
	
	private String pairToString(){
		if(empty()){
			return "(())";
		}else if(car().type == NIL){
			if(cdr().type == ScmObject.PAIR){
				return "(()" + cdr().toStringHelper() + ")";
			}else{
				return "(()" + cdr().toString() + ")";
			}
		}else if(cdr().type == ScmObject.NIL){
			return "(" + car().toString() + ")";
		}
		if(cdr().type == ScmObject.PAIR){
			return "(" + car().toString() + cdr().toStringHelper() + ")";
		}
		String rtn = "(" + car().toString() + " . " + cdr().toString() + ")";
		return rtn;
	}
	private String toStringHelper(){
		if(empty()){
			return " ()";
		}else if(cdr().type == ScmObject.NIL){
			return " " + car().toString();
		}else if( !(cdr().type == ScmObject.PAIR)){
			return " " + car().toString() + " . " + cdr().toString();
		}else{
			return " " + car().toString() + cdr().toStringHelper();
		}
	}
	private String vectToString(){
		ScmObject[] c = (ScmObject[]) content;
		String rtn = "< ";
		for(int i=0; i<c.length; i++){
			rtn = rtn + c[i].toString() + " ";
		}
		return rtn + ">";
	}
	private boolean empty(){
		return car().type == NIL && cdr().type == ScmObject.NIL;
	}
	
}
