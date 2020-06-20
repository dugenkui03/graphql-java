package graphql.language.node;

import graphql.masker.PublicApi;

/**
 * 标量值类型标记：BooleanValue、IntValue、FloatValue、StringValue等；
 */
@PublicApi
public interface ScalarValue<T extends Value> extends Value<T> {
}
