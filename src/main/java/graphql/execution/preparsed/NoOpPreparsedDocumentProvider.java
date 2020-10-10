package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.Internal;

import java.util.function.Function;

@Internal
public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    // 没有缓存，直接进行解析操作
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    // 标准用法是computeFunction计算后，使用ExecutionInput的字段作为key到一个map中，参见TestingPreparsedDocumentProvider
    @Override
    public PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        return parseAndValidateFunction.apply(executionInput);
    }
}
