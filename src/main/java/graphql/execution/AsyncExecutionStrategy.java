package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static graphql.collect.ImmutableKit.map;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        MergedSelectionSet fields = parameters.getFields();
        Set<String> fieldNames = fields.keySet();
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>(fieldNames.size());
        List<String> resolvedFields = new ArrayList<>(fieldNames.size());
        for (String fieldName : fieldNames) {
            //本质是个list
            MergedField currentField = fields.getSubField(fieldName);

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    //fixme
                    //  path(segment),this是parent、parent是当前节点
                    //  field()：当前要调用dataFetcher获取的节点；
                    //  parent()：父节点的策略函数。
                    //      注：父节点的当前字段是中的list<Field>是顶层字段；
                    //          path中的parent 和 segment 都是null；
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            resolvedFields.add(fieldName);
            CompletableFuture<FieldValueInfo> future = resolveFieldWithInfo(executionContext, newParameters);
            futures.add(future);
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        /**
         * 每一个字段对应个元素；
         * 即使有字段执行报错、已经在 resolveFieldWithInfo 进行了解析、封装为"错误结果"，
         * 因此在 Async.each 调用 allOf方法的时候，并不会有"一个错误导致整体错误"的事情。
         */
        Async.each(futures).whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, resolvedFields, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }
            List<CompletableFuture<ExecutionResult>> executionResultFuture = map(completeValueInfos, FieldValueInfo::getFieldValue);
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            Async.each(executionResultFuture).whenComplete(handleResultsConsumer);
        })
        // 如果上一步 whenComplete 计算发生异常，则再次处理。return 值会有相应的展现。
        .exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }
}