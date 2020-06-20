package graphql.masker;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * 标记字段、方法等，使得他们在测试时有比真正使用时有更高的可见性
 * Marks fields, methods etc as more visible than actually needed for testing purposes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, FIELD})
@Internal
public @interface VisibleForTesting {
}
