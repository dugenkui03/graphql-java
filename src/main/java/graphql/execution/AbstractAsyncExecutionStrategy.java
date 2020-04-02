package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;


public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy() {
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    /**
     * 处理结果
     */
    protected BiConsumer<List<ExecutionResult>, Throwable> handleResults(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        //void accept(T t, U u)处理两个参数且不返回数据：参数分别是处理结果和异常信息
        return (List<ExecutionResult> results, Throwable exception) -> {
            //如果包含异常，则直接处理异常并返回
            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }

            //fixme <字段名称,字段值>
            Map<String, Object> resolvedValuesByField = new LinkedHashMap<>();
            for (int i = 0; i < results.size(); i++) {
                ExecutionResult executionResult = results.get(i);
                String fieldName = fieldNames.get(i);
                resolvedValuesByField.put(fieldName, executionResult.getData());
            }

            /**
             * fixme
             *      将<字段名称,字段值>设置为ExecutionResultImpl的data，将上下文中的错误信息做为结果中的error
             *      overallResult包含结果对象
             */
            overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
        };
    }
}
