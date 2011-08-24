package org.goobs.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
