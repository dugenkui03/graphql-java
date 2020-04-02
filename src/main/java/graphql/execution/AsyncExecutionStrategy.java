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
     *fixme:  对每个字段都是异步执行的
     *        执行策略的全局入口：执行字段的取值和转换
     *
     * @param executionContext contains the top level execution parameters  执行上下文
     * @param parameters       contains the parameters holding the fields to be executed and source object 持有要解析的字段和父类返回值
     *
     * @return
     * @throws NonNullableFieldWasNullException
     */
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        /**
         * 1. instrument记录执行上下文、要解析的字段和父类返回值；
         * 2. 获取要执行的字段；
         * 3.
         */
        //instrument
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        //获取要解析的field？
        MergedSelectionSet fields = parameters.getFields();

        //被解析的字段名称和保存结果的feture
        List<String> resolvedFields = new ArrayList<>();
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();

        //遍历子实体:subFields.keySet()
        for(String fieldName:fields.keySet()){
            //获取子实体对应的对象<String_fieldName,MergedField_fieldInfo>
            MergedField currentField = fields.getSubField(fieldName);

            //Field field = currentField.get(0);
            //return field.getAlias() != null ? field.getAlias() : field.getName();
            String nameForCurrentField = mkNameForPath(currentField);
            ExecutionPath fieldPath = parameters.getPath().segment(nameForCurrentField);

            //策略参数
            ExecutionStrategyParameters newParameters =
                    //fxime：我看懂了，将parameters的当前字段、字段路径和参数设置为所求值
                    parameters.transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));


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
