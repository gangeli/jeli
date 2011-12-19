package org.goobs.database;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CompoundIndexList {
	CompoundIndex[] value();
}
