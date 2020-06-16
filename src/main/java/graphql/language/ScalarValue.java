package graphql.language;

import graphql.PublicApi;

/**
 * 标量值类型标记：BooleanValue、IntValue、FloatValue、StringValue等；
 */
@PublicApi
public interface ScalarValue<T extends Value> extends Value<T> {
}
