package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.defer.DeferSupport;
import graphql.execution.defer.DeferredCall;
import graphql.execution.defer.DeferredErrorSupport;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static graphql.execution.MergedSelectionSet.newMergedSelectionSet;

/**
 * fixme: 异步非阻塞 执行策略
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
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

    /**
     *fixme:
     *      执行策略的全局入口：执行字段的取值和转换
     *      This is the entry point to an execution strategy.  It will be passed the fields to execute and get values for.
     *
     * fixme 对每个字段都是异步执行的
     *
     * @param executionContext contains the top level execution parameters  执行上下文
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return
     * @throws NonNullableFieldWasNullException
     */
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        //instrument
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        /**
         *  获取其所有字段?当前类型的所有子实体
         */
        MergedSelectionSet fields = parameters.getFields();

        /**
         * 获取所有子实体的 key/名称
         */
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        //保存子实体的异步结果
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();
        //解析后的字段？list of fieldNames
        List<String> resolvedFields = new ArrayList<>();

        //遍历子实体
        for (String fieldName : fieldNames) {
            //获取子实体对应的对象<String_fieldName,MergedField_fieldInfo>
            MergedField currentField = fields.getSubField(fieldName);


            ExecutionPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            resolvedFields.add(fieldName);
            CompletableFuture<FieldValueInfo> future;

            if (isDeferred(executionContext, newParameters, currentField)) {
                executionStrategyCtx.onDeferredField(currentField);
                future = resolveFieldWithInfoToNull(executionContext, newParameters);
            } else {
                future = resolveFieldWithInfo(executionContext, newParameters);
            }
            futures.add(future);
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Async.each(futures).whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, resolvedFields, overallResult);
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

    private boolean isDeferred(ExecutionContext executionContext, ExecutionStrategyParameters parameters, MergedField currentField) {
        DeferSupport deferSupport = executionContext.getDeferSupport();
        if (deferSupport.checkForDeferDirective(currentField, executionContext.getVariables())) {
            DeferredErrorSupport errorSupport = new DeferredErrorSupport();

            // with a deferred field we are really resetting where we execute from, that is from this current field onwards
            Map<String, MergedField> fields = new LinkedHashMap<>();
            fields.put(currentField.getName(), currentField);

            ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                    {
                        MergedSelectionSet mergedSelectionSet = newMergedSelectionSet().subFields(fields).build();
                        builder.deferredErrorSupport(errorSupport)
                                .field(currentField)
                                .fields(mergedSelectionSet)
                                .parent(null) // this is a break in the parent -> child chain - its a new start effectively
                                .listSize(0)
                                .currentListIndex(0);
                    }
            );

            DeferredCall call = new DeferredCall(parameters.getPath(), deferredExecutionResult(executionContext, callParameters), errorSupport);
            deferSupport.enqueue(call);
            return true;
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Supplier<CompletableFuture<ExecutionResult>> deferredExecutionResult(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        return () -> {
            GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.getField().getSingleField());
            GraphQLObjectType fieldContainer = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();

            Instrumentation instrumentation = executionContext.getInstrumentation();
            DeferredFieldInstrumentationContext fieldCtx = instrumentation.beginDeferredField(
                    new InstrumentationDeferredFieldParameters(executionContext, parameters, fieldDef, createExecutionStepInfo(executionContext, parameters, fieldDef, fieldContainer))
            );
            CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
            fieldCtx.onDispatched(result);
            CompletableFuture<FieldValueInfo> fieldValueInfoFuture = resolveFieldWithInfo(executionContext, parameters);

            fieldValueInfoFuture.whenComplete((fieldValueInfo, throwable) -> {
                fieldCtx.onFieldValueInfo(fieldValueInfo);

                CompletableFuture<ExecutionResult> execResultFuture = fieldValueInfo.getFieldValue();
                execResultFuture = execResultFuture.whenComplete(fieldCtx::onCompleted);
                Async.copyResults(execResultFuture, result);
            });
            return result;
        };
    }
}
