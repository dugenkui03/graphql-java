package graphql.execution.preparsed;


import graphql.execution.ExecutionInput;

import java.util.function.Function;

/**
 * 不急性
 */
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
        return computeFunction.apply(executionInput);
    }
}
