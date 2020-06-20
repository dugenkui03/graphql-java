package graphql.execution

import graphql.error.ErrorType
import graphql.error.GraphQLError
import graphql.execution.exception.handler.DataFetcherExceptionHandlerResult
import graphql.language.node.SourceLocation
import spock.lang.Specification

class DataFetcherExceptionHandlerResultTest extends Specification {

    class CustomError implements GraphQLError {
        def msg

        CustomError(msg) {
            this.msg = msg
        }

        @Override
        String getMessage() {
            return msg
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return null
        }
    }

    def "builder works"() {
        when:
        def result = DataFetcherExceptionHandlerResult
                .newResult(new CustomError("First"))
                .error(new CustomError("Second"))
                .errors([new CustomError("Third"), new CustomError("Fourth")])
                .build()
        then:
        result.errors.collect { err -> err.getMessage() } == ["First", "Second", "Third", "Fourth"]
    }
}
