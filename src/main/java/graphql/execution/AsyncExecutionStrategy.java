package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext,
                                                      ExecutionStrategyParameters strategyParameters) throws NonNullableFieldWasNullException {

        // 获取全局Instrumentation工具
        Instrumentation instrumentation = executionContext.getInstrumentation();

        // MergedField
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, strategyParameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        //Map<aliasOrName,List<Field>>
        MergedSelectionSet mergedSelectionSet = strategyParameters.getFields();
        List<String> fieldNames = new ArrayList<>(mergedSelectionSet.keySet());
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();
        List<String> resolvedFields = new ArrayList<>();

        // Let fieldName be the name of the first entry in fields.
        // Note: This value is unaffected if an alias is used.
        for (String fieldName : fieldNames) {
            //本质是个list
            MergedField currentField = mergedSelectionSet.getSubField(fieldName);

            //todo
            ResultPath fieldPath = strategyParameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = strategyParameters
                    //fixme
                    //  path(segment),this是parent、parent是当前节点
                    //  field()：当前要调用dataFetcher获取的节点；
                    //  parent()：父节点的策略函数。
                    //      注：父节点的当前字段是中的list<Field>是顶层字段；
                    //          path中的parent 和 segment 都是null；
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(strategyParameters));

            resolvedFields.add(fieldName);
            //Let fieldType be the return type defined for the field fieldName of objectType.
            CompletableFuture<FieldValueInfo> future = resolveFieldWithInfo(executionContext, newParameters);
            futures.add(future);
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Async.each(futures).whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, resolvedFields, overallResult, strategyParameters);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }
            List<CompletableFuture<ExecutionResult>> executionResultFuture = completeValueInfos.stream().map(FieldValueInfo::getFieldValue).collect(Collectors.toList());
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            Async.each(executionResultFuture).whenComplete(handleResultsConsumer);
        }).exceptionally((ex) -> {
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
