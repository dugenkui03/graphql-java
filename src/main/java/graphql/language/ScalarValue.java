package graphql.language;

import graphql.PublicApi;

/**
 * value 类型标记
 */
@PublicApi
public interface ScalarValue<T extends Value> extends Value<T> {
}
