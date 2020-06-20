package graphql.execution.batched;

import graphql.execution.strategy.AsyncExecutionStrategy;
import graphql.execution.DataFetcher;

/**
 * See {@link Batched}.
 * @deprecated This has been deprecated in favour of using {@link AsyncExecutionStrategy} and {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 */
@Deprecated
public interface BatchedDataFetcher extends DataFetcher {
    // Marker interface
}
