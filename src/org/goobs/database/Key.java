package org.goobs.database;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {
	String name();
	int id() default -1;
	int length() default -1;
	Class type() default java.lang.Object.class;
}
