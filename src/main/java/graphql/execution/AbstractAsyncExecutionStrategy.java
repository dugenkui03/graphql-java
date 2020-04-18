package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;


public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }


    /**
     * 定义处理dataFetcher结果的函数：void accept(T t, U u);
     *
     * @param executionContext 执行上下文
     * @param fieldNames 这一层处理的所有字段名称
     * @param overallResult 刚刚进来的时候是空的，上层定义： CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
     * @return 定义的BiConsumer函数： void accept(T t, U u);
     */
    protected BiConsumer<List<ExecutionResult>, Throwable> handleResults(ExecutionContext executionContext,
                                                                         List<String> fieldNames,
                                                                         CompletableFuture<ExecutionResult> overallResult) {

        return (List<ExecutionResult> results, Throwable exception) -> {
            /**
             * 如果包含异常，则直接处理异常并返回
             */
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
