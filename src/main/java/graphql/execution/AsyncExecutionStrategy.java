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
     * 使用给定的异常处理器、创建异步策略
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use 使用的异常处理器
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    /**
     * fixme:   执行策略的全局入口，传递要查询的字段(fields, not field definetion)，并且获取其值
     *
     * @param executionContext 执行上下文
     * @param strategyParameters 传递给执行策略的参数
     *
     * @throws NonNullableFieldWasNullException 如果有non-null字段解析为null，则跑异常、data返回null
     */
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters strategyParameters) throws NonNullableFieldWasNullException {
        /**
         * InstrumentationContext，策略参数instrument
         */
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, strategyParameters);
        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        /**
         * 本次执行查询的字段
         */
        MergedSelectionSet fields = strategyParameters.getFields();

        /**
         * 中间对象：被解析的字段名称和保存结果的feture
         */
        List<String> resolvedFields = new ArrayList<>();
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();

        //遍历每个字段
        for(String fieldName:fields.keySet()){
            //获取某个字段集合，所谓的集合就是同一类型的、可以merge的字段集合
            MergedField currentField = fields.getSubField(fieldName);

            //查询字段集中第一个字段的别名
            String nameForCurrentField = mkNameForPath(currentField);
            ExecutionPath fieldPath = strategyParameters.getPath().segment(nameForCurrentField);

            //策略参数
            ExecutionStrategyParameters newParameters =
                    //fxime：我看懂了，将parameters的当前字段、字段路径和参数设置为所求值
                    strategyParameters.transform(builder -> builder.field(currentField).path(fieldPath).parent(strategyParameters));


            CompletableFuture<FieldValueInfo> future;

            // 如果当前字段是延迟字段，获取控制FieldValueInfo
            if (isDeferred(executionContext, newParameters, currentField)) {
                executionStrategyCtx.onDeferredField(currentField);
                //获取 ExecutionContext 和 ExecutionStrategyParameters 对应的空值FieldValueInfo
                future = resolveFieldWithInfoToNull(executionContext, newParameters);
            }
            //当前字段不是延迟字段，fixme 调用dataFetcher和解析字段值，都在resolveFieldWithInfo中
            else {
                future = resolveFieldWithInfo(executionContext, newParameters);
            }
            //添加被解析的字段名称和保存结果的feture
            resolvedFields.add(fieldName);
            futures.add(future);
        }// end of for

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Async.each(futures).whenComplete((completeValueInfos, throwable) -> {
            /**
             * 构造处理函数
             */
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer =
                    //执行上下文、解析的属性名称和保存所有结果的CompletableFuture
                    handleResults(executionContext, resolvedFields, overallResult);

            /**
             * 异常信息不为空、则执行处理函数
             */
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }

            /**
             * fixme 对每个字段的异步任务做转换
             */
            List<CompletableFuture<ExecutionResult>> executionResultFuture =
                    //遍历结果，并对之前保存每个字段异步任务的集合做映射：CompletableFuture<FieldValueInfo> ——> CompletableFuture<ExecutionResult>
                    completeValueInfos.stream().map(FieldValueInfo::getFieldValue).collect(Collectors.toList());

            //instrument
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

    /**
     * 是否是 延迟字段
     * @param executionContext 执行上下文
     * @param parameters 执行策略参数
     */
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
