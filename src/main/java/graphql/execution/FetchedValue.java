package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLError;
import graphql.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Internal
public class FetchedValue {
    //拆箱后的结果
    private final Object fetchedValue;
    //dataFetcher返回的data，如果没有Optional包装、和fetchedValue是一样的
    private final Object rawFetchedValue;

    private final Object localContext;
    private final ImmutableList<GraphQLError> errors;

    private FetchedValue(Object fetchedValue, Object rawFetchedValue, List<GraphQLError> errors, Object localContext) {
        this.fetchedValue = fetchedValue;
        this.rawFetchedValue = rawFetchedValue;
        this.errors = ImmutableList.copyOf(errors);
        this.localContext = localContext;
    }

    /*
     * the unboxed value meaning not Optional, not DataFetcherResult etc
     */
    public Object getFetchedValue() {
        return fetchedValue;
    }

    public Object getRawFetchedValue() {
        return rawFetchedValue;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public Object getLocalContext() {
        return localContext;
    }

    public FetchedValue transform(Consumer<Builder> builderConsumer) {
        Builder builder = newFetchedValue(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public String toString() {
        return "FetchedValue{" +
                "fetchedValue=" + fetchedValue +
                ", rawFetchedValue=" + rawFetchedValue +
                ", localContext=" + localContext +
                ", errors=" + errors +
                '}';
    }

    public static Builder newFetchedValue() {
        return new Builder();
    }

    public static Builder newFetchedValue(FetchedValue otherValue) {
        return new Builder()
                .fetchedValue(otherValue.getFetchedValue())
                .rawFetchedValue(otherValue.getRawFetchedValue())
                .errors(otherValue.getErrors())
                .localContext(otherValue.getLocalContext())
                ;
    }

    public static class Builder {

        private Object fetchedValue;
        private Object rawFetchedValue;
        private Object localContext;
        private List<GraphQLError> errors = new ArrayList<>();

        public Builder fetchedValue(Object fetchedValue) {
            this.fetchedValue = fetchedValue;
            return this;
        }

        public Builder rawFetchedValue(Object rawFetchedValue) {
            this.rawFetchedValue = rawFetchedValue;
            return this;
        }

        public Builder localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public Builder errors(List<GraphQLError> errors) {
            this.errors = errors;
            return this;
        }

        public FetchedValue build() {
            return new FetchedValue(fetchedValue, rawFetchedValue, errors, localContext);
        }
    }
}