package graphql.schema;


import graphql.masker.PublicApi;
import graphql.execution.TrivialDataFetcher;
import graphql.execution.DataFetcher;

/**
 * 总是返回相同值的DataFetcher
 * A {@link DataFetcher} that always returns the same value
 */
@PublicApi
public class StaticDataFetcher implements DataFetcher, TrivialDataFetcher {


    private final Object value;

    public StaticDataFetcher(Object value) {
        this.value = value;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        return value;
    }

}
