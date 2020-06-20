package graphql.execution

import graphql.error.ErrorType
import graphql.error.GraphQLError
import graphql.TestUtil
import graphql.execution.exception.handler.DataFetcherExceptionHandler
import graphql.execution.exception.handler.DataFetcherExceptionHandlerResult
import graphql.execution.strategy.AsyncExecutionStrategy
import graphql.language.node.SourceLocation
import spock.lang.Specification

class DataFetcherExceptionHandlerTest extends Specification {

    class CustomError implements GraphQLError {
        String msg
        SourceLocation sourceLocation

        CustomError(String msg, SourceLocation sourceLocation) {
            this.msg = msg
            this.sourceLocation = sourceLocation
        }

        @Override
        String getMessage() {
            return msg
        }

        @Override
        List<SourceLocation> getLocations() {
            return [sourceLocation]
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }
    }

    def "integration test to prove custom error handle can be made"() {
        DataFetcherExceptionHandler handler = {
            params -> DataFetcherExceptionHandlerResult.newResult().error(new CustomError("The thing went " + params.getException().getMessage(), params.getSourceLocation())).build()
        }

        def dataFetchers = [
                Query: [field: { env -> throw new RuntimeException("BANG") } as DataFetcher]
        ]

        def graphQL = TestUtil.graphQL('''
            type Query {
                field : String
            }
        ''', dataFetchers)
                .queryExecutionStrategy(new AsyncExecutionStrategy(handler))
                .build()
        when:
        def result = graphQL.execute(ExecutionInput.newExecutionInput().query(' { field }'))
        then:
        !result.errors.isEmpty()
        result.errors[0].message == "The thing went BANG"
    }
}
