package graphql.execution.batched;

import graphql.GraphQLException;
import graphql.PublicApi;


@Deprecated
@PublicApi
public class BatchAssertionFailed extends GraphQLException {

    private static final long serialVersionUID = -5381285633600023972L;

    public BatchAssertionFailed() {
        super();
    }

    public BatchAssertionFailed(String message) {
        super(message);
    }

    public BatchAssertionFailed(String message, Throwable cause) {
        super(message, cause);
    }

    public BatchAssertionFailed(Throwable cause) {
        super(cause);
    }
}
