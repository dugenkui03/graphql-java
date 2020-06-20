package graphql.schema.idl.errors;

import graphql.error.ErrorType;
import graphql.error.GraphQLError;
import graphql.error.GraphQLException;
import graphql.error.GraphqlErrorHelper;
import graphql.language.node.Node;
import graphql.language.node.SourceLocation;

import java.util.Collections;
import java.util.List;

class BaseError extends GraphQLException implements GraphQLError {
    protected static final SourceLocation NO_WHERE = new SourceLocation(-1, -1);

    private final Node node;

    public BaseError(Node node, String msg) {
        super(msg);
        this.node = node;
    }

    public static String lineCol(Node node) {
        SourceLocation sourceLocation = node.getSourceLocation() == null ? NO_WHERE : node.getSourceLocation();
        return String.format("[@%d:%d]", sourceLocation.getLine(), sourceLocation.getColumn());
    }

    @Override
    public List<SourceLocation> getLocations() {
        return node == null ? Collections.singletonList(NO_WHERE) : Collections.singletonList(node.getSourceLocation());
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public String toString() {
        return getMessage();
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
