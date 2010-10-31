package org.goobs.scheme;

import java.math.BigInteger;

public class SoftPrimitive extends ScmObject implements Primitive{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8533677408564750070L;

	protected static String MODULO = "modulo";
	
	private String id;
	//private Scheme scm;
	
	protected SoftPrimitive(String i, Scheme s){
		super("#[subr " + i + "]", ScmObject.FUNCTION);
		id = i;
		//scm = s;
	}

	@Override
	public ScmObject apply(ScmObject args, SchemeThreadPool.SchemeThread thread) throws SchemeException{
		if(id.equals(SoftPrimitive.MODULO)){
			return modulo(args);
		}else{
			throw new SchemeException("[INTERNAL]: cannot find primitive: " + id);
		}	
	}
	
	private static ScmObject modulo(ScmObject args){
		if (args.isNil() || args.cdr().isNil() || !args.cdr().cdr().isNil()) {
			throw new SchemeException("bad number of parameters");
		}
		ScmObject num = args.car();
		ScmObject n = args.cdr().car();
		if(num.isType(ScmObject.INTEGER)){
			if(n.isType(ScmObject.INTEGER)){
				return new ScmObject( (Integer) num.getContent() % (Integer) n.getContent(), ScmObject.INTEGER);
			}else if(n.isType(ScmObject.BIGINT)){
				BigInteger bnum = new BigInteger(num.toString());
				bnum = bnum.mod(new BigInteger(n.toString()));
				if(bnum.shiftRight(32).equals(BigInteger.ZERO)){
					return new ScmObject( new Integer(bnum.intValue()), ScmObject.INTEGER );
				}else{
					return new ScmObject( bnum, ScmObject.BIGINT );
				}
			}else{
				throw new SchemeException("not an integer: " + n);
			}
		}else if(num.isType(ScmObject.BIGINT)){
			BigInteger bnum = (BigInteger) num.getContent();
			if(n.isType(ScmObject.INTEGER)){
				bnum = bnum.mod(new BigInteger(n.toString()));
				if(bnum.shiftRight(32).equals(BigInteger.ZERO)){
					return new ScmObject( new Integer(bnum.intValue()), ScmObject.INTEGER );
				}else{
					return new ScmObject( bnum, ScmObject.BIGINT );
				}
			}else if(n.isType(ScmObject.BIGINT)){
				bnum = bnum.mod((BigInteger) n.getContent());
				if(bnum.shiftRight(32).equals(BigInteger.ZERO)){
					return new ScmObject( new Integer(bnum.intValue()), ScmObject.INTEGER );
				}else{
					return new ScmObject( bnum, ScmObject.BIGINT );
				}
			}else{
				throw new SchemeException("not an integer: " + n);
			}
		}else{
			throw new SchemeException("not an integer: " + num);
		}
	}
	
}
