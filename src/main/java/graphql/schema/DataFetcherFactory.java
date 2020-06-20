package graphql.schema;

import graphql.masker.PublicSpi;
import graphql.execution.DataFetcher;

/**
 * A DataFetcherFactory allows a level of indirection in providing {@link DataFetcher}s for graphql fields.
 *
 * For example if you are using an IoC container such as Spring or Guice, you can use this indirection to give you
 * per request late binding of a data fetcher with its dependencies injected in.
 *
 * @param <T> the type of DataFetcher
 */
@PublicSpi
public interface DataFetcherFactory<T> {

    /**
     * Returns a {@link DataFetcher}
     *
     * @param environment the environment that needs the data fetcher
     *
     * @return a data fetcher
     */
    DataFetcher<T> get(DataFetcherFactoryEnvironment environment);

}
