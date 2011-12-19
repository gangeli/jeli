package org.goobs.database;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PrimaryKey{
	String name() default "id";
	int id() default -1;
	boolean autoIncrement() default false;
}
