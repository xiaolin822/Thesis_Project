
package dk.ku.di.dms.vms.modb.api.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExternalMsg {
    boolean emit() default true;
    String value() default "";
}
