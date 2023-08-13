package xyz.wagyourtail.unimined.jarmodagent.transformer.annotation;

import java.lang.annotation.*;

/**
 * skips the next annotation. or hard target.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
public @interface DontRemap {

    /**
     * if this is present, only skips remapping if matches.
     * this is useful if you only want to skip @CTarget, or vis versa.
     */
    Class<? extends Annotation>[] value() default {};

    /**
     * skip remapping until the next @DontRemap (that one doesn't just act as an end, it still does its default behavior)
     * @return
     */
    boolean skip() default false;

}
