package graphql.schema;


import graphql.PublicApi;
import graphql.TrivialDataFetcher;

/**
 * A {@link graphql.schema.DataFetcher} that always returns the same value
 *
 * 总是返回构造参数中的值。
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
