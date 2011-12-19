package org.goobs.database;

import org.goobs.database.Index.Type;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CompoundIndex {
	
	public String[] fields();
	
	public Type type() default Type.BTREE;
	
}
