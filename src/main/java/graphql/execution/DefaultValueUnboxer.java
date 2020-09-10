package graphql.execution;

import graphql.Internal;
import graphql.PublicApi;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Public API because it should be used as a delegate when implementing a custom {@link ValueUnboxer}
 */
@PublicApi
public class DefaultValueUnboxer implements ValueUnboxer {

    @Override
    public Object unbox(final Object object) {
        return unboxValue(object);
    }

    @Internal // used by next-gen at the moment
    public static Object unboxValue(Object result) {
        if (result instanceof Optional) {
            Optional optional = (Optional) result;
            return optional.orElse(null);
        } else if (result instanceof OptionalInt) {
            OptionalInt optional = (OptionalInt) result;
            return optional.isPresent() ? optional.getAsInt() : null;
        } else if (result instanceof OptionalDouble) {
            OptionalDouble optional = (OptionalDouble) result;
            return optional.isPresent() ? optional.getAsDouble() : null;
        } else if (result instanceof OptionalLong) {
            OptionalLong optional = (OptionalLong) result;
            return optional.isPresent() ? optional.getAsLong() : null;
        }

        return result;
    }
}