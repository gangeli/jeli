package org.goobs.scheme;

public interface Primitive {

	public ScmObject apply(ScmObject arg, SchemeThreadPool.SchemeThread thread);
}
