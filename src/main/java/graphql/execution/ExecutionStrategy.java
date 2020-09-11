package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicSpi;
import graphql.SerializationError;
import graphql.TrivialDataFetcher;
import graphql.TypeMismatchError;
import graphql.UnresolvedTypeError;
import graphql.execution.directives.QueryDirectives;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.util.FpKit;
import graphql.util.LogKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static graphql.execution.Async.exceptionallyCompletedFuture;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.FieldValueInfo.CompleteValueType.ENUM;
import static graphql.execution.FieldValueInfo.CompleteValueType.LIST;
import static graphql.execution.FieldValueInfo.CompleteValueType.NULL;
import static graphql.execution.FieldValueInfo.CompleteValueType.OBJECT;
import static graphql.execution.FieldValueInfo.CompleteValueType.SCALAR;
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;
import static graphql.schema.GraphQLTypeUtil.isList;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * An execution strategy is give a list of fields from the graphql query to execute and find values for using a recursive strategy.
 * <pre>
 *     query {
 *          friends {
 *              id
 *              name
 *              friends {
 *                  id
 *                  name
 *              }
 *          }
 *          enemies {
 *              id
 *              name
 *              allies {
 *                  id
 *                  name
 *              }
 *          }
 *     }
 *
 * </pre>
 * <p>
 * Given the graphql query above, an execution strategy will be called for the top level fields 'friends' and 'enemies' and it will be asked to find an object
 * to describe them.  Because they are both complex object types, it needs to descend down that query and start fetching and completing
 * fields such as 'id','name' and other complex fields such as 'friends' and 'allies', by recursively calling to itself to execute these lower
 * field layers
 * <p>
 * The execution of a field has two phases, first a raw object must be fetched for a field via a {@link DataFetcher} which
 * is defined on the {@link GraphQLFieldDefinition}.  This object must then be 'completed' into a suitable value, either as a scalar/enum type via
 * coercion or if its a complex object type by recursively calling the execution strategy for the lower level fields.
 * <p>
 * The first phase (data fetching) is handled by the method {@link #fetchField(ExecutionContext, ExecutionStrategyParameters)}
 * <p>
 * The second phase (value completion) is handled by the methods {@link #completeField(ExecutionContext, ExecutionStrategyParameters, FetchedValue)}
 * and the other "completeXXX" methods.
 * <p>
 * The order of fields fetching and completion is up to the execution strategy. As the graphql specification
 * <a href="http://facebook.github.io/graphql/#sec-Normal-and-Serial-Execution">http://facebook.github.io/graphql/#sec-Normal-and-Serial-Execution</a> says:
 * <blockquote>
 * Normally the executor can execute the entries in a grouped field set in whatever order it chooses (often in parallel). Because
 * the resolution of fields other than top-level mutation fields must always be side effect-free and idempotent, the
 * execution order must not affect the result, and hence the server has the freedom to execute the
 * field entries in whatever order it deems optimal.
 * </blockquote>
 * <p>
 * So in the case above you could execute the fields depth first ('friends' and its sub fields then do 'enemies' and its sub fields or it
 * could do breadth first ('fiends' and 'enemies' data fetch first and then all the sub fields) or in parallel via asynchronous
 * facilities like {@link CompletableFuture}s.
 * <p>
 * {@link #execute(ExecutionContext, ExecutionStrategyParameters)} is the entry point of the execution strategy.
 */
@PublicSpi
@SuppressWarnings("FutureReturnValueIgnored")
public abstract class ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);
    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();
    private final ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    private final ResolveType resolvedType = new ResolveType();

    protected final DataFetcherExceptionHandler dataFetcherExceptionHandler;

    /**
     * The default execution strategy constructor uses the {@link SimpleDataFetcherExceptionHandler}
     * for data fetching errors.
     */
    protected ExecutionStrategy() {
        dataFetcherExceptionHandler = new SimpleDataFetcherExceptionHandler();
    }

    /**
     * The consumers of the execution strategy can pass in a {@link DataFetcherExceptionHandler} to better
     * decide what do when a data fetching error happens
     *
     * @param dataFetcherExceptionHandler the callback invoked if an exception happens during data fetching
     */
    protected ExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        this.dataFetcherExceptionHandler = dataFetcherExceptionHandler;
    }

    /**
     * This is the entry point to an execution strategy.  It will be passed the fields to execute and get values for.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return a promise to an {@link ExecutionResult}
     * @throws NonNullableFieldWasNullException in the future if a non null field resolves to a null value
     */
    public abstract CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException;

    /**
     * Called to fetch a value for a field and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed {@link ExecutionResult} is returned.
     * <p>
     * An execution strategy can iterate the fields to be executed and call this method for each one
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return a promise to an {@link ExecutionResult}
     * @throws NonNullableFieldWasNullException in the future if a non null field resolves to a null value
     */
    protected CompletableFuture<ExecutionResult> resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        return resolveFieldWithInfo(executionContext, parameters).thenCompose(FieldValueInfo::getFieldValue);
    }

    /**
     * Called to fetch a value for a field and its extra runtime info and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed {@link graphql.execution.FieldValueInfo} is returned.
     * <p>
     * An execution strategy can iterate the fields to be executed and call this method for each one
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param strategyParameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a promise to a {@link FieldValueInfo}
     * @throws NonNullableFieldWasNullException in the {@link FieldValueInfo#getFieldValue()} future if a non null field resolves to a null value
     */
    protected CompletableFuture<FieldValueInfo> resolveFieldWithInfo(ExecutionContext executionContext,
                                                                     ExecutionStrategyParameters strategyParameters) {

        /**
        * 使用内省、当前要访问的字段类型
        *      getField()获取的是MergedField(MergedField本质是一个list<Field>,Field只会对应一个GraphQLFieldDefinition);
        */
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, strategyParameters, strategyParameters.getField().getSingleField());
        Supplier<ExecutionStepInfo> executionStepInfo = FpKit.memoize(() -> createExecutionStepInfo(executionContext, strategyParameters, fieldDef, null));

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(
                new InstrumentationFieldParameters(executionContext, executionStepInfo)
        );

        //请求数据
        CompletableFuture<FetchedValue> fetchFieldFuture = fetchField(executionContext, strategyParameters);
        //解析数据
        CompletableFuture<FieldValueInfo> result = fetchFieldFuture.thenApply(
                (fetchedValue) -> completeField(executionContext, strategyParameters, fetchedValue)
        );

        //thenCompose 对结果进一步处理，输入是第一个的future的结果
        CompletableFuture<ExecutionResult> executionResultFuture = result.thenCompose(FieldValueInfo::getFieldValue);

        fieldCtx.onDispatched(executionResultFuture);
        executionResultFuture.whenComplete(fieldCtx::onCompleted);
        return result;
    }

    protected CompletableFuture<FieldValueInfo> resolveFieldWithInfoToNull(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        FetchedValue fetchedValue = FetchedValue.newFetchedValue().build();
        FieldValueInfo fieldValueInfo = completeField(executionContext, parameters, fetchedValue);
        return CompletableFuture.completedFuture(fieldValueInfo);
    }


    /**
     * Called to fetch a value for a field from the {@link DataFetcher} associated with the field
     * {@link GraphQLFieldDefinition}.
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return a promise to a fetched object
     * @throws NonNullableFieldWasNullException in the future if a non null field resolves to a null value
     */
    protected CompletableFuture<FetchedValue> fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedField field = parameters.getField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();

        //GraphQLObjectType包含 getFieldDefinition(name) 方法，名称从fieldgetSingleField()中获取，最终调用的Introspection方法检查了内省和可见性信息。
        //大部分情况执行的都是 GraphQLFieldDefinition fieldDefinition = parentType.getFieldDefinition(field.getSingleField().getName());
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field.getSingleField());

        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        GraphQLOutputType fieldType = fieldDef.getType();

        // DataFetchingFieldSelectionSet and QueryDirectives is a supplier of sorts - eg a lazy pattern
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, parameters.getField());
        QueryDirectives queryDirectives = new QueryDirectivesImpl(field, executionContext.getGraphQLSchema(), executionContext.getVariables());

        //定义两个回调方法、如果解析的字段不需要参数、例如PropertyDataFetcher，则不不执行取值函数
        // if the DF (like PropertyDataFetcher) does not use the arguments of execution step info then don't build any

        /**
         * fixme
         *      使用参数、或者 ExecutionStepInfo 的时候，才调用此回调方法进行获取；
         *      参数在DataFetcher中使用
         *      ExecutionStepInfo可以参考如下方法的调用位置、一般不使用
         *             {@link graphql.schema.DataFetchingEnvironmentImpl#getExecutionStepInfo}
         */
        //解析、获取参数的回调方法
        Supplier<Map<String, Object>> argumentValues = FpKit.memoize(
                () -> valuesResolver.getArgumentValues(codeRegistry, fieldDef.getArguments(), field.getArguments(), executionContext.getVariables()));

        //解析、获取ExecutionStepInfo的回调方法
        // if the DF (like PropertyDataFetcher) does not use the arguments of execution step info then dont build any
        Supplier<ExecutionStepInfo> executionStepInfo = FpKit.memoize(
                () -> createExecutionStepInfo(executionContext, parameters, fieldDef, parentType));

        //获取DF环境变量
        DataFetchingEnvironment dataFetchingEnvironment = newDataFetchingEnvironment(executionContext)
                .source(parameters.getSource())
                .localContext(parameters.getLocalContext())
                .arguments(argumentValues)
                //两个回调函数
                .fieldDefinition(fieldDef)
                .executionStepInfo(executionStepInfo)

                .mergedField(parameters.getField())
                .fieldType(fieldType)
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .queryDirectives(queryDirectives)
                .build();

        /**
         * 获取字段对应的DF，参见{@link graphql.schema.GraphQLCodeRegistry#getDataFetcherImpl}
         *            使用字段名称从系统fetcher集合中获取；
         *            使用字段坐标<类型名称-字段名称>、从dataFetcherMap中获取；
         *            使用默认的fetcher。
         */
        DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parentType, fieldDef);

        //开始获取字段前：执行上下文、dataFetchingEnvironment、dataFetcher是否是trivial等
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationFieldFetchParameters instrumentationFieldFetchParams =
                new InstrumentationFieldFetchParameters(executionContext, fieldDef, dataFetchingEnvironment, parameters, dataFetcher instanceof TrivialDataFetcher);
        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(instrumentationFieldFetchParams);
        //instrument DF
        dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, instrumentationFieldFetchParams);


        CompletableFuture<Object> fetchedValue;
        try {
            Object fetchedValueRaw = dataFetcher.get(dataFetchingEnvironment);
            fetchedValue = Async.toCompletableFuture(fetchedValueRaw);
        } catch (Exception e) {
            //异常结果
            fetchedValue = new CompletableFuture<>();
            fetchedValue.completeExceptionally(e);
        }

        //instrument回调
        fetchCtx.onDispatched(fetchedValue);

        return fetchedValue
                .handle(
                        (result, exception) -> {
                            //instrument
                            fetchCtx.onCompleted(result, exception);
                            //如果结果有异常、则记录fetcher异常。fetcher异常就是在这里捕获、记录的
                            if (exception != null) {
                                handleFetchingException(executionContext, parameters, dataFetchingEnvironment, exception);
                                return null;
                            }
                            //如果没有异常、则返回CompletableFuture<result>。
                            // fixme：handle 和 下边的 thenApply只是处理流程的编排，即使这个函数结束执行后也可能没有执行到此逻辑
                            else {
                                return result;
                            }
                        }
                )
                .thenApply(result ->
                        unboxPossibleDataFetcherResult(executionContext, parameters, result)
                );
    }

    FetchedValue unboxPossibleDataFetcherResult(ExecutionContext executionContext,
                                                ExecutionStrategyParameters parameters,
                                                Object result) {

        if (result instanceof DataFetcherResult) {
            //noinspection unchecked
            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult) result;
            if (dataFetcherResult.isMapRelativeErrors()) {
                dataFetcherResult.getErrors().stream()
                        .map(relError -> new AbsoluteGraphQLError(parameters, relError))
                        .forEach(executionContext::addError);
            } else {
                dataFetcherResult.getErrors().forEach(executionContext::addError);
            }

            Object localContext = dataFetcherResult.getLocalContext();
            if (localContext == null) {
                // if the field returns nothing then they get the context of their parent field
                localContext = parameters.getLocalContext();
            }
            return FetchedValue.newFetchedValue()
                    .fetchedValue(executionContext.getValueUnboxer().unbox(dataFetcherResult.getData()))
                    .rawFetchedValue(dataFetcherResult.getData())
                    .errors(dataFetcherResult.getErrors())
                    .localContext(localContext)
                    .build();
        } else {
            return FetchedValue.newFetchedValue()
                    .fetchedValue(executionContext.getValueUnboxer().unbox(result))
                    .rawFetchedValue(result)
                    .localContext(parameters.getLocalContext())
                    .build();
        }
    }

    private void handleFetchingException(ExecutionContext executionContext,
                                         ExecutionStrategyParameters parameters,
                                         DataFetchingEnvironment environment,
                                         Throwable e) {
        DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(e)
                .build();

        DataFetcherExceptionHandlerResult handlerResult = dataFetcherExceptionHandler.onException(handlerParameters);
        handlerResult.getErrors().forEach(executionContext::addError);

    }

    /**
     * Called to complete a field based on the type of the field.
     * <p>
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fetchedValue     the fetched raw value
     * @return a {@link FieldValueInfo}
     * @throws NonNullableFieldWasNullException in the {@link FieldValueInfo#getFieldValue()} future if a non null field resolves to a null value
     */
    protected FieldValueInfo completeField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, FetchedValue fetchedValue) {
        Field field = parameters.getField().getSingleField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
        ExecutionStepInfo executionStepInfo = createExecutionStepInfo(executionContext, parameters, fieldDef, parentType);

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, fetchedValue);

        InstrumentationContext<ExecutionResult> ctxCompleteField = instrumentation.beginFieldComplete(instrumentationParams);

        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                builder.executionStepInfo(executionStepInfo)
                        .source(fetchedValue.getFetchedValue())
                        .localContext(fetchedValue.getLocalContext())
                        .nonNullFieldValidator(nonNullableFieldValidator)
        );

        if (log.isDebugEnabled()) {
            log.debug("'{}' completing field '{}'...", executionContext.getExecutionId(), executionStepInfo.getPath());
        }

        FieldValueInfo fieldValueInfo = completeValue(executionContext, newParameters);

        CompletableFuture<ExecutionResult> executionResultFuture = fieldValueInfo.getFieldValue();
        ctxCompleteField.onDispatched(executionResultFuture);
        executionResultFuture.whenComplete(ctxCompleteField::onCompleted);
        return fieldValueInfo;
    }


    /**
     * Called to complete a value for a field based on the type of the field.
     * <p>
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return a {@link FieldValueInfo}
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected FieldValueInfo completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        //
        ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();
        Object result = executionContext.getValueUnboxer().unbox(parameters.getSource());
        GraphQLType fieldType = executionStepInfo.getUnwrappedNonNullType();
        CompletableFuture<ExecutionResult> fieldValue;

        //如果结果是null，则不管是什么类型的字段，都不会在进一步获取其值、除非是空map；
        if (result == null) {
            fieldValue = completeValueForNull(parameters);
            return FieldValueInfo.newFieldValueInfo(NULL).fieldValue(fieldValue).build();
        }
        //如果是list类型结果
        else if (isList(fieldType)) {
            //for循环中递归调用回completeValue
            return completeValueForList(executionContext, parameters, result);
        } else if (fieldType instanceof GraphQLScalarType) {
            fieldValue = completeValueForScalar(executionContext, parameters, (GraphQLScalarType) fieldType, result);
            return FieldValueInfo.newFieldValueInfo(SCALAR).fieldValue(fieldValue).build();
        } else if (fieldType instanceof GraphQLEnumType) {
            fieldValue = completeValueForEnum(executionContext, parameters, (GraphQLEnumType) fieldType, result);
            return FieldValueInfo.newFieldValueInfo(ENUM).fieldValue(fieldValue).build();
        }

        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper
        // fixme 当递归到这里的时候、fetcher获取的值是 对象、接口或者union
        GraphQLObjectType resolvedObjectType;
        try {
            resolvedObjectType = resolveType(executionContext, parameters, fieldType);
            fieldValue = completeValueForObject(executionContext, parameters, resolvedObjectType, result);
        } catch (UnresolvedTypeException ex) {
            // consider the result to be null and add the error on the context
            handleUnresolvedTypeProblem(executionContext, parameters, ex);
            // and validate the field is nullable, if non-nullable throw exception
            parameters.getNonNullFieldValidator().validate(parameters.getPath(), null);
            // complete the field as null
            fieldValue = completedFuture(new ExecutionResultImpl(null, null));
        }
        return FieldValueInfo.newFieldValueInfo(OBJECT).fieldValue(fieldValue).build();
    }

    private void handleUnresolvedTypeProblem(ExecutionContext context, ExecutionStrategyParameters parameters, UnresolvedTypeException e) {
        UnresolvedTypeError error = new UnresolvedTypeError(parameters.getPath(), parameters.getExecutionStepInfo(), e);
        logNotSafe.warn(error.getMessage(), e);
        context.addError(error);

    }

    private CompletableFuture<ExecutionResult> completeValueForNull(ExecutionStrategyParameters strategyParameters) {
        return Async.tryCatch(
                //Supplier: T get();
                () -> {
                    //nullValue的值就是null，此操作是为了验证该字段在graphql类型系统定义是非空的；
                    Object nullValue = strategyParameters.getNonNullFieldValidator().validate(strategyParameters.getPath(), null);
                    return completedFuture(new ExecutionResultImpl(nullValue, null));
                }
        );
    }

    /**
     * Called to complete a list of value for a field based on a list type.  This iterates the values and calls
     * {@link #completeValue(ExecutionContext, ExecutionStrategyParameters)} for each value.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param result           the result to complete, raw result
     * @return a {@link FieldValueInfo}
     */
    protected FieldValueInfo completeValueForList(
            ExecutionContext executionContext, //执行上下文
            ExecutionStrategyParameters parameters,//该字段的策略参数
            Object result) {//该字段的list类型结果
        Iterable<Object> resultIterable = toIterable(executionContext, parameters, result);
        try {
            resultIterable = parameters.getNonNullFieldValidator().validate(parameters.getPath(), resultIterable);
        } catch (NonNullableFieldWasNullException e) {
            return FieldValueInfo.newFieldValueInfo(LIST).fieldValue(exceptionallyCompletedFuture(e)).build();
        }
        if (resultIterable == null) {
            return FieldValueInfo.newFieldValueInfo(LIST).fieldValue(completedFuture(new ExecutionResultImpl(null, null))).build();
        }
        return completeValueForList(executionContext, parameters, resultIterable);
    }

    /**
     * Called to complete a list of value for a field based on a list type.  This iterates the values and calls
     * {@link #completeValue(ExecutionContext, ExecutionStrategyParameters)} for each value.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param iterableValues   the values to complete, can't be null
     * @return a {@link FieldValueInfo}
     */
    protected FieldValueInfo completeValueForList(ExecutionContext executionContext,
                                                  ExecutionStrategyParameters parameters, Iterable<Object> iterableValues) {
        //list类型结果
        Collection<Object> values = FpKit.toCollection(iterableValues);

        ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();

        InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, values);
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<ExecutionResult> completeListCtx =
                instrumentation.beginFieldListComplete(instrumentationParams);

        List<FieldValueInfo> fieldValueInfos = new ArrayList<>(values.size());
        int index = 0;
        for (Object item : values) {
            ResultPath indexedPath = parameters.getPath().segment(index);

            ExecutionStepInfo stepInfoForListElement = executionStepInfoFactory.newExecutionStepInfoForListElement(executionStepInfo, index);

            NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, stepInfoForListElement);

            int finalIndex = index;
            FetchedValue value = unboxPossibleDataFetcherResult(executionContext, parameters, item);

            ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                    builder.executionStepInfo(stepInfoForListElement)
                            .nonNullFieldValidator(nonNullableFieldValidator)
                            .listSize(values.size())
                            .localContext(value.getLocalContext())
                            .currentListIndex(finalIndex)
                            .path(indexedPath)
                            .source(value.getFetchedValue())
            );
            //保存该元素结果
            fieldValueInfos.add(completeValue(executionContext, newParameters));
            index++;
        }

        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.each(fieldValueInfos, (item, i) -> item.getFieldValue());

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        completeListCtx.onDispatched(overallResult);

        resultsFuture.whenComplete((results, exception) -> {
            if (exception != null) {
                ExecutionResult executionResult = handleNonNullException(executionContext, overallResult, exception);
                completeListCtx.onCompleted(executionResult, exception);
                return;
            }
            List<Object> completedResults = new ArrayList<>(results.size());
            for (ExecutionResult completedValue : results) {
                completedResults.add(completedValue.getData());
            }
            ExecutionResultImpl executionResult = new ExecutionResultImpl(completedResults, null);
            overallResult.complete(executionResult);
        });
        overallResult.whenComplete(completeListCtx::onCompleted);

        return FieldValueInfo.newFieldValueInfo(LIST)
                //todo
                .fieldValue(overallResult)
                //todo
                .fieldValueInfos(fieldValueInfos)
                .build();
    }

    /**
     * Called to turn an object into a scalar value according to the {@link GraphQLScalarType} by asking that scalar type to coerce the object
     * into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param scalarType       the type of the scalar
     * @param result           the result to be coerced
     * @return a promise to an {@link ExecutionResult}
     */
    protected CompletableFuture<ExecutionResult> completeValueForScalar(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLScalarType scalarType, Object result) {
        Object serialized;
        try {
            serialized = scalarType.getCoercing().serialize(result);
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }

        // TODO: fix that: this should not be handled here
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            serialized = null;
        }
        try {
            serialized = parameters.getNonNullFieldValidator().validate(parameters.getPath(), serialized);
        } catch (NonNullableFieldWasNullException e) {
            return exceptionallyCompletedFuture(e);
        }
        return completedFuture(new ExecutionResultImpl(serialized, null));
    }

    /**
     * Called to turn an object into a enum value according to the {@link GraphQLEnumType} by asking that enum type to coerce the object into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param enumType         the type of the enum
     * @param result           the result to be coerced
     * @return a promise to an {@link ExecutionResult}
     */
    protected CompletableFuture<ExecutionResult> completeValueForEnum(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLEnumType enumType, Object result) {
        Object serialized;
        try {
            serialized = enumType.serialize(result);
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }
        try {
            serialized = parameters.getNonNullFieldValidator().validate(parameters.getPath(), serialized);
        } catch (NonNullableFieldWasNullException e) {
            return exceptionallyCompletedFuture(e);
        }
        return completedFuture(new ExecutionResultImpl(serialized, null));
    }

    /**
     * 将dataFetcher请求的java对象转换为graphql对象值。
     *
     * Called to turn an java object value into an graphql object value
     *
     * @param executionContext contains the top level execution parameters
     * @param strategyParameters contains the parameters holding the fields to be executed and source object
     *
     * @param resolvedObjectType the resolved object type
     * @param result             the result to be coerced
     * @return a promise to an {@link ExecutionResult}
     */
    protected CompletableFuture<ExecutionResult> completeValueForObject(
            ExecutionContext executionContext, //执行上下文：
            ExecutionStrategyParameters strategyParameters, //策略上下文：每一个字段都有一个策略上下文对象
            GraphQLObjectType resolvedObjectType, //具体的对象类型：interface和union也可以看作某一对象类型
            Object result) { //该字段对应的值
        ExecutionStepInfo executionStepInfo = strategyParameters.getExecutionStepInfo();

        //step 1.1：使用schema、字段类型、片段定义以及变量，获取字段收集参数
        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedObjectType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        //step 1.2: 收集字段参数
        MergedSelectionSet subFields = fieldCollector.collectFields(collectorParameters, strategyParameters.getField());

        //step2: 创建新的ExecutionStepInfo对象：把type换成要解析的对象类型就行
        ExecutionStepInfo newExecutionStepInfo = executionStepInfo.changeTypeWithPreservedNonNull(resolvedObjectType);

        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, newExecutionStepInfo);

        /**
         * fixme
         *      每一个策略上下文都有一个ExecutionStrategyParameters对象；
         *      每次递归回 Strategy.execute() 都会重置 ExecutionStepInfo、MergedSelectionSet、source；
         */
        ExecutionStrategyParameters newParameters = strategyParameters.transform(builder ->
                builder.executionStepInfo(newExecutionStepInfo)
                        .fields(subFields)
                        .nonNullFieldValidator(nonNullableFieldValidator)
                        .source(result)
        );

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.
        // 从执行上下文调用，确保我们从 更改策略 改为了 查询策略。
        return executionContext.getQueryStrategy().execute(executionContext, newParameters);
    }

    @SuppressWarnings("SameReturnValue")
    private Object handleCoercionProblem(ExecutionContext context, ExecutionStrategyParameters parameters, CoercingSerializeException e) {
        SerializationError error = new SerializationError(parameters.getPath(), e);
        logNotSafe.warn(error.getMessage(), e);
        context.addError(error);


        return null;
    }


    /**
     * Converts an object that is known to should be an Iterable into one
     *
     * @param result the result object
     * @return an Iterable from that object
     * @throws java.lang.ClassCastException if its not an Iterable
     */
    @SuppressWarnings("unchecked")
    protected Iterable<Object> toIterable(Object result) {
        return FpKit.toCollection(result);
    }

    protected GraphQLObjectType resolveType(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLType fieldType) {
        return resolvedType.resolveType(executionContext, parameters.getField(), parameters.getSource(), parameters.getExecutionStepInfo().getArguments(), fieldType);
    }


    protected Iterable<Object> toIterable(ExecutionContext context, ExecutionStrategyParameters parameters, Object result) {
        if (result.getClass().isArray() || result instanceof Iterable) {
            return toIterable(result);
        }

        handleTypeMismatchProblem(context, parameters, result);
        return null;
    }

    private void handleTypeMismatchProblem(ExecutionContext context, ExecutionStrategyParameters parameters, Object result) {
        TypeMismatchError error = new TypeMismatchError(parameters.getPath(), parameters.getExecutionStepInfo().getUnwrappedNonNullType());
        logNotSafe.warn("{} got {}", error.getMessage(), result.getClass());
        context.addError(error);

    }


    /**
     * Called to discover the field definition give the current parameters and the AST {@link Field}
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param field            the field to find the definition of
     * @return a {@link GraphQLFieldDefinition}
     */
    protected GraphQLFieldDefinition getFieldDef(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Field field) {
        //parent一开始是query类型、不难找，以后递归获取子类型就行
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        return getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
    }

    /**
     * Called to discover the field definition give the current parameters and the AST {@link Field}
     *
     * @param schema     the schema in play
     * @param parentType the parent type of the field
     * @param field      the field to find the definition of
     * @return a {@link GraphQLFieldDefinition}
     */
    protected GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
        return Introspection.getFieldDef(schema, parentType, field.getName());
    }

    /**
     * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability),
     * <p>
     * If a non nullable child field type actually resolves to a null value and the parent type is nullable
     * then the parent must in fact become null
     * so we use exceptions to indicate this special case.  However if the parent is in fact a non nullable type
     * itself then we need to bubble that upwards again until we get to the root in which case the result
     * is meant to be null.
     *
     * @param e this indicates that a null value was returned for a non null field, which needs to cause the parent field
     *          to become null OR continue on as an exception
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e) throws NonNullableFieldWasNullException {
        ExecutionStepInfo executionStepInfo = e.getExecutionStepInfo();
        if (executionStepInfo.hasParent() && executionStepInfo.getParent().isNonNullType()) {
            throw new NonNullableFieldWasNullException(e);
        }
    }

    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e, CompletableFuture<?> completableFuture) throws NonNullableFieldWasNullException {
        ExecutionStepInfo executionStepInfo = e.getExecutionStepInfo();
        if (executionStepInfo.hasParent() && executionStepInfo.getParent().isNonNullType()) {
            completableFuture.completeExceptionally(new NonNullableFieldWasNullException(e));
        }
    }

    protected ExecutionResult handleNonNullException(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result, Throwable e) {
        ExecutionResult executionResult = null;
        List<GraphQLError> errors = new ArrayList<>(executionContext.getErrors());
        Throwable underlyingException = e;
        if (e instanceof CompletionException) {
            underlyingException = e.getCause();
        }
        if (underlyingException instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) underlyingException, result);
            if (!result.isDone()) {
                executionResult = new ExecutionResultImpl(null, errors);
                result.complete(executionResult);
            }
        } else if (underlyingException instanceof AbortExecutionException) {
            AbortExecutionException abortException = (AbortExecutionException) underlyingException;
            executionResult = abortException.toExecutionResult();
            result.complete(executionResult);
        } else {
            result.completeExceptionally(e);
        }
        return executionResult;
    }


    /**
     * Builds the type info hierarchy for the current field
     *
     * @param executionContext the execution context  in play
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fieldDefinition  the field definition to build type info for
     * @param fieldContainer   the field container
     * @return a new type info
     */
    protected ExecutionStepInfo createExecutionStepInfo(ExecutionContext executionContext,
                                                        ExecutionStrategyParameters parameters,
                                                        GraphQLFieldDefinition fieldDefinition,
                                                        GraphQLObjectType fieldContainer) {
        MergedField field = parameters.getField();
        ExecutionStepInfo parentStepInfo = parameters.getExecutionStepInfo();
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<GraphQLArgument> fieldArgDefs = fieldDefinition.getArguments();
        Map<String, Object> argumentValues = Collections.emptyMap();
        //
        // no need to create args at all if there are none on the field def
        //
        if (!fieldArgDefs.isEmpty()) {
            List<Argument> fieldArgs = field.getArguments();
            GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
            argumentValues = valuesResolver.getArgumentValues(codeRegistry, fieldArgDefs, fieldArgs, executionContext.getVariables());
        }


        return newExecutionStepInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .fieldContainer(fieldContainer)
                .field(field)
                .path(parameters.getPath())
                .parentInfo(parentStepInfo)
                .arguments(argumentValues)
                .build();
    }


    @Internal
    public static String mkNameForPath(Field currentField) {
        return mkNameForPath(Collections.singletonList(currentField));
    }

    @Internal
    public static String mkNameForPath(MergedField mergedField) {
        return mkNameForPath(mergedField.getFields());
    }


    @Internal
    public static String mkNameForPath(List<Field> currentField) {
        Field field = currentField.get(0);
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }
}
