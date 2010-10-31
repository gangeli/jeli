package org.goobs.scheme;

import java.util.HashMap;

public class Environment {
	
	private Environment parent;
	private HashMap <ScmObject, ScmObject> bindings;
	private boolean writeLock = false;
	private static boolean resolveBindings = true;
	
	protected Environment(Environment p){
		parent = p;
		bindings = new HashMap <ScmObject, ScmObject> ();
	}
	
	protected ScmObject apply(ScmObject proc, ScmObject args, SchemeThreadPool.SchemeThread thread){
		return proc.apply(args, thread);
	}
	
	//exp must be a list
	protected ScmObject eval(ScmObject exp, SchemeThreadPool.SchemeThread thread) throws SchemeException{
		//expression is a binding
		if(exp.isType(ScmObject.BINDING)){
			if(resolveBindings){
				return getBinding(exp);
			}else{
				ScmObject rtn = getBindingOrNull(exp);
				if(rtn == null){
					return exp;
				}else{
					return rtn;
				}
			}
		}
		//expression is a function
		if(exp.isType(ScmObject.PAIR)){
			ScmObject func = eval(exp.car(), thread);
			if(func.isType(ScmObject.SPECIAL_FORM)){
				return specialApply(func, exp.cdr(),thread);
			}else{
				try {
					return apply(func, mapEval(exp.cdr(), thread), thread);
				} catch (SchemeException e) {
					//ignore repetitive 'eval:' tags
					if(e.getMessage().substring(0,5).equals("eval:")){
						throw e;
					}
					throw new SchemeException("eval: " + e.getMessage() + ": " + exp);
				}
			}
		}
		//expression must be a literal
		return exp;
	}
	
	private ScmObject specialApply(ScmObject func, ScmObject args, SchemeThreadPool.SchemeThread thread){
		//and
		if(func.getContent().equals("#[subr and]")){
			return andHelper(args, thread);
		//cond
		}else if(func.getContent().equals("#[subr cond]")){
			return condHelper(args, thread);
		//define
		}else if(func.getContent().equals("#[subr define]")){
			if(args.getType() != ScmObject.PAIR){
				throw new SchemeException("define: bad definition: " + args);
			}
			if(args.car().getType() != ScmObject.BINDING){
				throw new SchemeException("define: bad variable name: " + args.car());
			}
			putBinding(args.car(), eval(args.cdr().car(), thread));
			return new ScmObject(args.car(), ScmObject.WORD);
		//do
		}else if(func.getContent().equals("#[subr do]")){
			if(args.isNil()){
				return new ScmObject("okay", ScmObject.WORD);
			}
			return doHelper(args, thread);
		//if
		}else if(func.getContent().equals("#[subr if]")){
			if(args.isNil() || args.cdr().isNil() || args.cdr().cdr().isNil()
					|| !args.cdr().cdr().cdr().isNil()){
				throw new SchemeException("if: bad syntax: " + ScmObject.makePair(func, args));
			}
			if( !eval(args.car(), thread).isFalse() ){
				return eval(args.cdr().car(), thread);
			}else{
				return eval(args.cdr().cdr().car(), thread);
			}
		//lambda
		}else if(func.getContent().equals("#[subr lambda]")){
			return ScmObject.makeLambda(args, this);
		//or
		}else if(func.getContent().equals("#[subr or]")){
			return orHelper(args, thread);
		//quote
		}else if(func.getContent().equals("#[subr quote]")){
			if (args.isNil() || !args.cdr().isNil() ) {
				throw new SchemeException("quote: bad number of parameters");
			}
			return new ScmObject(args.car(), ScmObject.WORD);
		//set!
		}else if(func.getContent().equals("#[subr set!]")){
			if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
				throw new SchemeException("set!: bad number of parameters");
			}else if(!args.car().isType(ScmObject.BINDING)){
				throw new SchemeException("set!: not a binding: " + args.car());
			}
			setBinding( args.car(), eval(args.cdr().car(), thread) );
			return new ScmObject("okay", ScmObject.WORD);
		//set-car!
		}else if(func.getContent().equals("#[subr set-car!]")){
			if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
				throw new SchemeException("set-car!: bad number of parameters");
			}else if(!args.car().isType(ScmObject.BINDING)){
				throw new SchemeException("set-car!: not a binding: " + args.car());
			}
			getBinding(args.car()).replaceCdr( eval(args.cdr().car(), thread) );
			return new ScmObject("okay", ScmObject.WORD);
		//set-cdr!
		}else if(func.getContent().equals("#[subr set-cdr!]")){
			if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
				throw new SchemeException("set-cdr!: bad number of parameters");
			}else if(!args.car().isType(ScmObject.BINDING)){
				throw new SchemeException("set-cdr!: not a binding: " + args.car());
			}
			getBinding(args.car()).replaceCdr( eval(args.cdr().car(), thread) );
			return new ScmObject("okay", ScmObject.WORD);
		//let-par
		}else if(func.getContent().equals("#[subr let-par]")){
			//(error checking)
			if(args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()){
				throw new SchemeException("let-par: bad number of parameters");
			}
			if(Scheme.auxPool == null){
				throw new SchemeException("parallelization has been disabled");
			}
			if(thread == null){
				throw new SchemeException("cannot parallelize in non-parallelizable mode");
			}
			//(get two parts of let-par)
			ScmObject letClauses = args.car();
			ScmObject finalExp = args.cdr().car();
			Scheme.auxPool.beginAppend(thread);
			//(append tasks)
			while(!letClauses.isNil()){
				ScmObject clause = letClauses.car();
				if(clause.cdr().isNil() || !clause.cdr().cdr().isNil()){
					throw new SchemeException("let-par clauses should have only one argument");
				}
				ScmObject binding = clause.car();
				ScmObject toRun  = clause.cdr().car();
				ScmObject toFill = ScmObject.makeFalse();	//dummy object
				Environment protectedEnv = extendLockedEnvironment();	//extends calling environment
				putBinding(binding, toFill);
				Scheme.auxPool.append(toRun, protectedEnv, toFill, thread);
				letClauses = letClauses.cdr();
			}
			//(run final statement)
			Scheme.auxPool.endAppendAndWait(thread);
			return eval(finalExp, thread);

		}else{
			throw new SchemeException("[INTERNAL]: unimplemented special form: " + func);
		}
		
	}
	
	private ScmObject andHelper(ScmObject args, SchemeThreadPool.SchemeThread thread){
		if(args.isType(ScmObject.NIL)){
			return ScmObject.makeTrue();
		}else if( eval(args.car(), thread).isType(ScmObject.FALSE) ){
			return ScmObject.makeFalse();
		}else{
			return andHelper(args.cdr(), thread);
		}
	}
	
	//preds is not nil and is a list
	private ScmObject condHelper(ScmObject preds, SchemeThreadPool.SchemeThread thread){
		if(preds.isType(ScmObject.NIL)){
			throw new SchemeException("cond: no conditions are satisfied");
		}
		ScmObject current = preds.car();
		if(current.car().getContent().equals("else") && current.car().isType(ScmObject.BINDING)){
			return eval(current.cdr().car(), thread);
		}else if(eval(current.car(), thread).isFalse()){
			return condHelper(preds.cdr(), thread);
		}else{
			return eval(current.cdr().car(), thread);
		}
	}
	
	//exps is not nil as precondition and exps is a list
	private ScmObject doHelper(ScmObject exps, SchemeThreadPool.SchemeThread thread){
		if(exps.cdr().isNil()){
			return eval(exps.car(), thread);
		}else{
			eval(exps.car(), thread);
			return doHelper(exps.cdr(), thread);
		}
	}
	
	private ScmObject orHelper(ScmObject args, SchemeThreadPool.SchemeThread thread){
		if(args.isType(ScmObject.NIL)){
			return ScmObject.makeFalse();
		}else if( eval(args.car(), thread).isType(ScmObject.TRUE) ){
			return ScmObject.makeTrue();
		}else{
			return orHelper(args.cdr(), thread);
		}
	}
	
	private ScmObject mapEval(ScmObject exps, SchemeThreadPool.SchemeThread thread){
		if(exps.isNil()){
			return ScmObject.makeNil();
		}else{
			return ScmObject.makePair(eval(exps.car(), thread), mapEval(exps.cdr(), thread));
		}
	}
	
	protected Environment extendLockedEnvironment(){
		Environment child = new Environment(this);
		child.writeLock = true;
		return child;
	}
	
	protected void putBinding(ScmObject binding, ScmObject value){
		bindings.put(binding, value);
	}
	
	protected void setBinding(ScmObject binding, ScmObject value){
		ScmObject old = null;
		if(writeLock){
			//(we should not look in parent bindings - thread safety)
			old = bindings.get(binding);
			if(old == null){
				throw new SchemeException("Unbound (or write-locked) variable: " + binding);
			}
		}else{
			//(get the binding)
			old = getBinding(binding);
		}
		//can't set! nil
		if(old.isType(ScmObject.NIL)){
			putBinding(binding, value);
			return;
		}else{
			//set variable
			old.setTo(value);
		}
	}
	
	private ScmObject getBindingOrNull(ScmObject binding){
		ScmObject rtn = bindings.get(binding);
		//if not in this environment
		if(rtn == null){
			if(parent == null){
				return null;
			}
			//check parent environments, passing along the exception if not found
			return parent.getBindingOrNull(binding);
		}else{
			return rtn;
		}
	}

	protected ScmObject getBinding(ScmObject binding){
		ScmObject rtn = getBindingOrNull(binding);
		if(rtn == null){
			throw new SchemeException("Unbound variable: " + binding);
		}else{
			return rtn;
		}

	}
	
	
	protected ScmObject getNonglobalBinding(ScmObject binding)throws SchemeException{
		if(parent == null){
			return null;
		}
		ScmObject rtn = bindings.get(binding);
		//if not in this environment
		if(rtn == null){
			//check parent environments, passing along the exception if not found
			return parent.getNonglobalBinding(binding);
		}else{
			return rtn;
		}
	}
	
	
	protected static void setResolveBindings(boolean rb){
		resolveBindings = rb;
	}
	
	protected static boolean getResolveBindings(){
		return resolveBindings;
	}
	
	
}
