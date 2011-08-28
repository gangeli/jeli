package org.goobs.scheme;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;



public abstract class ExternalPrimitive extends ScmObject implements Primitive{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -461731379219216198L;




	public ExternalPrimitive(){
		this("?");
	}
	
	public ExternalPrimitive(String name){
		super("#[extr " + name + "]", ScmObject.FUNCTION);
	}
	
	
	private static ScmObject makeVector(Object[] elems){
		ScmObject[] rtn = new ScmObject[elems.length];
		for(int i=0; i<elems.length; i++){
			rtn[i] = schemify(elems[i]);
		}
		return new ScmObject(rtn, ScmObject.VECTOR);
	}
	
	private static ScmObject makePair(Iterator <Object> iter){
		if(!iter.hasNext()){
			return ScmObject.makeNil();
		}
		return ScmObject.makePair(schemify(iter.next()), makePair(iter));
	}
	
	@SuppressWarnings("unchecked")
	private static ScmObject schemify(Object obj){
		if(obj instanceof Integer){
			return ScmObject.specify(obj.toString());
		}else if(obj instanceof Double){
			return ScmObject.specify(obj.toString());
		}else if(obj instanceof Boolean){
			if((Boolean) obj){
				return ScmObject.makeTrue();
			}else{
				return ScmObject.makeFalse();
			}
		}else if(obj == null){
			return ScmObject.makeNil();
		}else if(obj instanceof String){
			return ScmObject.specify(obj.toString());
		}else if(obj instanceof Object[]){
			return makeVector((Object[]) obj);
		}else if(obj instanceof List){
			return makePair(((List <Object>) obj).iterator());
		}else{
			throw new SchemeException("[INTERNAL]: invalid java object to schemify: " + obj);
		}
	}
	
	
	
	
	
	
	private static List <Object> makeList(ScmObject pair){
		ArrayList <Object> rtn = new ArrayList <Object> ();
		while(!pair.isNil()){
			if(!pair.isType(ScmObject.PAIR)){
				throw new SchemeException("apply: cannot accept non-list pairs for external primitives: " + pair);
			}
			rtn.add(javafy(pair.car()));
			pair = pair.cdr();
		}
		return rtn;
	}
	
	private static Object[] makeArray(ScmObject vector){
		ScmObject[] elems = (ScmObject[]) vector.getContent();
		Object[] rtn = new Object[elems.length];
		for(int i=0; i<elems.length; i++){
			rtn[i] = javafy(elems[i]);
		}
		return rtn;
	}
	
	private static Object javafy(ScmObject obj){
		switch(obj.getType()){
		case ScmObject.NIL:
			return null;
		case ScmObject.TRUE:
			return true;
		case ScmObject.FALSE:
			return false;
		case ScmObject.INTEGER:
			return (Integer) obj.getContent();
		case ScmObject.DOUBLE:
			return (Double) obj.getContent();
		case ScmObject.BIGINT:
			try {
				return Integer.parseInt(obj.getClass().toString());
			} catch (NumberFormatException e) {
				throw new SchemeException("apply: cannot accept BigInteger type for external primitives: " + obj);
			}
		case ScmObject.BIGDEC:
			try {
				return Double.parseDouble(obj.getClass().toString());
			} catch (NumberFormatException e) {
				throw new SchemeException("apply: cannot accept BigDecimal type for external primitives: " + obj);
			}
		case ScmObject.WORD:
		case ScmObject.STRING:
			return obj.getContent().toString();
		case ScmObject.PAIR:
			return makeList(obj);
		case ScmObject.VECTOR:
			return makeArray(obj);
		default:
			throw new SchemeException("[INTERNAL]: Trying to javafy an invalid object type");
		}
	}
	
	@Override
	public ScmObject apply(ScmObject arg, SchemeThreadPool.SchemeThread thread){
		return schemify(apply(javafy(arg)));
	}

	
	
	
	
	
	public abstract Object apply(Object arg);
	
	
	
	
	
//	public static void main(String[] args){
//		System.out.println(schemify("hello"));
//		System.out.println(schemify(5));
//		System.out.println(schemify(new String[]{"hello", "there", "world"}));
//		DBList tmp = new LinkedList();
//		tmp.add("hello");
//		tmp.add(7);
//		DBList tmp2 = new LinkedList();
//		tmp2.add("world");
//		tmp2.add(3.8);
//		tmp.add(tmp2);
//		System.out.println(schemify(tmp));
//	}
	
}
