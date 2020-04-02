package graphql;

import graphql.schema.DataFetcher;


/**
 * 标记一个Datafetcher不中要，这样的话就不会被TracingInstrument追踪性能了，例如PropertyDataFetcher就是TrivialDataFetcher
 *
 * Mark a DataFetcher as trivial:
 * If a data fetcher is simply mapping data from an object to a field, it can be considered a trivial data fetcher for the purposes
 * of tracing and so on.
 */
@PublicSpi
public interface TrivialDataFetcher<T> extends DataFetcher<T> {
}
