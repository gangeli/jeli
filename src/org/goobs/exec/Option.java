package org.goobs.exec;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Option {
	String name() default "";

	String gloss() default "";

	boolean required() default false;
	
	String alt() default "";
}
