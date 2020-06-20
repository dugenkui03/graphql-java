package graphql.relay;

import graphql.error.ErrorType;
import graphql.error.GraphQLError;
import graphql.error.GraphqlErrorHelper;
import graphql.language.node.SourceLocation;

import java.util.List;

import static graphql.error.ErrorType.DataFetchingException;

public class InvalidCursorException extends RuntimeException implements GraphQLError {

    InvalidCursorException(String message) {
        this(message, null);
    }

    InvalidCursorException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return DataFetchingException;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }

}
