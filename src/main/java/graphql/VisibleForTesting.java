package graphql;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Marks fields, methods etc as more visible than actually needed for testing purposes.
 *
 * 注解在构造器、方法和字段上，使其在测试过程中具有比其实际更高的可见行
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, FIELD})
@Internal
public @interface VisibleForTesting {
}
