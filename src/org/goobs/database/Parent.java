package org.goobs.database;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Parent{
	String localField();
	String parentField();
	Index.Type indexType() default Index.Type.HASH;
}
