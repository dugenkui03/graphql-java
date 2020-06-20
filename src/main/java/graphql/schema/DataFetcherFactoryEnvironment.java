package graphql.schema;

import graphql.masker.PublicApi;
import graphql.execution.DataFetcher;

/**
 * This is passed to a {@link graphql.schema.DataFetcherFactory} when it is invoked to
 * get a {@link DataFetcher}
 */
@PublicApi
public class DataFetcherFactoryEnvironment {
    private final GraphQLFieldDefinition fieldDefinition;

    DataFetcherFactoryEnvironment(GraphQLFieldDefinition fieldDefinition) {
        this.fieldDefinition = fieldDefinition;
    }

    /**
     * @return the field that needs a {@link DataFetcher}
     */
    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public static Builder newDataFetchingFactoryEnvironment() {
        return new Builder();
    }

    static class Builder {
        GraphQLFieldDefinition fieldDefinition;

        public Builder fieldDefinition(GraphQLFieldDefinition fieldDefinition) {
            this.fieldDefinition = fieldDefinition;
            return this;
        }

        public DataFetcherFactoryEnvironment build() {
            return new DataFetcherFactoryEnvironment(fieldDefinition);
        }
    }
}
