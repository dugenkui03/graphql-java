package graphql.schema;

import graphql.masker.PublicApi;
import graphql.execution.DataFetcher;

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * A helper for {@link graphql.schema.DataFetcherFactory}
 */
@PublicApi
public class DataFetcherFactories {

    /**
     * 返回创建参数类型DataFetcher的工厂。
     *
     * Creates a {@link graphql.schema.DataFetcherFactory} that always returns the provided {@link DataFetcher}
     *
     * @param dataFetcher the data fetcher to always return
     * @param <T>         the type of the data fetcher
     *
     * @return a data fetcher factory that always returns the provided data fetcher
     */
    public static <T> DataFetcherFactory<T> useDataFetcher(DataFetcher<T> dataFetcher) {
        return fieldDefinition -> dataFetcher;
    }

    /**
     * 对DataFetcher的结果进行在一次的封装。
     *
     * This helper function allows you to wrap an existing data fetcher and map the value once it completes.  It helps you handle
     * values that might be {@link  java.util.concurrent.CompletionStage} returned values as well as plain old objects.
     *
     * @param delegateDataFetcher the original data fetcher that is present on a {@link graphql.schema.GraphQLFieldDefinition} say
     * @param mapFunction         the bi function to apply to the original value
     *
     * @return a new data fetcher that wraps the provided data fetcher
     */
    public static DataFetcher wrapDataFetcher(DataFetcher delegateDataFetcher, BiFunction<DataFetchingEnvironment, Object, Object> mapFunction) {
        return environment -> {
            Object value = delegateDataFetcher.get(environment);
            if (value instanceof CompletionStage) {
                //noinspection unchecked
                return ((CompletionStage<Object>) value).thenApply(v -> mapFunction.apply(environment, v));
            } else {
                return mapFunction.apply(environment, value);
            }
        };
    }

}
