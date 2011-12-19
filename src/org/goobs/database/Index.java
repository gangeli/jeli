package org.goobs.database;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Index {
	public static enum Type{
		HASH,
		BTREE,
		RTREE,
		NONE
	}
	
	public Type type() default Type.BTREE;
	
}
