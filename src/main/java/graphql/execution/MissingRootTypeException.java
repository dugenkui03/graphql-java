package graphql.execution;

import java.util.Collections;
import java.util.List;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

/**
 * This is thrown if a query is attempting to perform an operation not defined in the GraphQL schema
 *
 * 查询dsl要执行的 查询、更新、订阅 操作没有在schema中定义
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
