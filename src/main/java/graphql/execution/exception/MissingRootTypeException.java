package graphql.execution.exception;

import java.util.Collections;
import java.util.List;

import graphql.error.ErrorType;
import graphql.error.GraphQLError;
import graphql.error.GraphQLException;
import graphql.masker.PublicApi;
import graphql.language.node.SourceLocation;

/**
 * This is thrown if a query is attempting to perform an operation not defined in the GraphQL schema
 */
@PublicApi
public class MissingRootTypeException extends GraphQLException implements GraphQLError {
    private List<SourceLocation> sourceLocations;

    public MissingRootTypeException(String message, SourceLocation sourceLocation) {
        super(message);
        this.sourceLocations = sourceLocation == null ? null : Collections.singletonList(sourceLocation);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.OperationNotSupported;
    }
}
