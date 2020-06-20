package graphql.execution.nextgen;

import graphql.masker.Internal;
import graphql.execution.DataFetcher;

@Internal
public interface BatchedDataFetcher<T> extends DataFetcher<T> {
}
