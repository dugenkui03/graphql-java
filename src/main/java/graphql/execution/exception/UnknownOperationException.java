package graphql.execution.exception;

import graphql.error.GraphQLException;
import graphql.masker.PublicApi;

/**
 * This is thrown if multiple operations are defined in the query and
 * the operation name is missing or there is no matching operation name
 * contained in the GraphQL query.
 */
@PublicApi
public class UnknownOperationException extends GraphQLException {

    public UnknownOperationException(String message) {
        super(message);
    }
}
