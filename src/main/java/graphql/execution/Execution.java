package graphql.execution;


import graphql.DeferredExecutionResult;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.defer.DeferSupport;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.util.LogKit;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Internal
public class Execution {

    /**
     * fixme: 字段收集器和值解析器
     *        1. FieldCollector.ConditionalNodes 字段包含了对skip和include的实现;
     *        2. ValuesResolver 变量到参数的强转
     */
    private final FieldCollector fieldCollector = new FieldCollector();
    private final ValuesResolver valuesResolver = new ValuesResolver();
    /**
     * 执行策略
     */
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    /**
     * 自定义Instrumentation
     */
    private final Instrumentation instrumentation;
    /**
     * 拆箱器
     */
    private ValueUnboxer valueUnboxer;

    //执行策略、拆箱器、instrumentation等对象
    public Execution(ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Instrumentation instrumentation, ValueUnboxer valueUnboxer) {
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new AsyncExecutionStrategy();
        this.instrumentation = instrumentation;
        this.valueUnboxer = valueUnboxer;
    }

    /**
     * @param document 校验通过的查询文档
     * @param graphQLSchema 类型系统模式
     * @param executionInput 输入：包括变量
     * @param instrumentationState instrument
     * @return 执行查询并返回结果
     */
    public CompletableFuture<ExecutionResult> execute(Document document, GraphQLSchema graphQLSchema, ExecutionId executionId, ExecutionInput executionInput, InstrumentationState instrumentationState) {

        /**
         * fixme
         *      step1: GetOperationResult：操作定义和片段
         */
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        //获取片段定义：名称、类型、指令集合、字段集合(SelectionSet)
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        //操作定义：名称、类型、指令集合、字段集合(SelectionSet)和变量
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;


        /**
         * fixme
         *      step-2：从变量中获取参数参数的具体值；
         *          1. 获取查询变量，既入参map；
         *          2.  获取document中的变量定义：名称、类型、默认值，既 query ($phone: String = "15901331549")；
         *          3. 使用valuesResolver.coerceVariableValues进行强转，获取 <参数名称,参数值>map；
         *     示例：
         *        比如我们输入包含<"mobilePhone","15901331549">的map键值对；
         *        比如我们有查询
         *              query($mobilePhone:String = "110"){
         *                  user(phone:$mobilePhone){
         *                      name
         *                  }
         *              }
         *              首先会根据query后的变量集合key、获取键值对中对应key的数据、如果有必填参数不存在则抛异常；$mobilePhone
         *              进行查询的时候，会将phone的key替换成对应的值，程序可能如下:
         *                     Map<String,Object> arguemnts=new HashMap();
         *                     for(Entry entry : typeArgument){
         *                          String argName=entry.getValue();
         *                          Object value=coercedVariables.get(argName);
         *                          arguments.put(entry.getKey,value);
         *                     }
         *
         */

        //获取查询变量，既入参map
        Map<String, Object> inputVariables = executionInput.getVariables();
        //获取document中的变量定义：名称、类型、默认值- query ($phone: String = "15901331549")
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        //进行强转
        Map<String, Object> coercedVariables;
        try {
            coercedVariables = valuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, inputVariables);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            throw rte;
        }

        /**
         * fixme
         *      1. 定义操作操作上下文，包含操作流程中所有重要的东西；
         *      2. 修改操作上下文：
         *              b. schema
         *              c. 执行策略
         *              d. 值解析器
         *              e. 文档；
         *              d. 强转后的值和输入等信息
         *
         */
        ExecutionContext executionContext = newExecutionContextBuilder()
                //instrument
                .instrumentation(instrumentation)
                .instrumentationState(instrumentationState)
                .executionId(executionId)
                //schema
                .graphQLSchema(graphQLSchema)
                //执行策略：并行、串行等
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                //值解析器
                .valueUnboxer(valueUnboxer)
                /**
                 * 查询输入：上下文、root对象、解析文档、变量等
                 */
                //执行上下文
                .context(executionInput.getContext())
                //root对象
                .root(executionInput.getRoot())
                //包含的片段
                .fragmentsByName(fragmentsByName)
                //强转后的变量值：变量名称是typeArgument的value： <a,b>、<b,c> -> <a,c>
                .variables(coercedVariables)
                //查询文档
                .document(document)
                //操作定义：名称、类型、指令集合、字段集合(SelectionSet)和变量
                .operationDefinition(operationDefinition)
                //与执行相关的 DataLoaderRegistry
                .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
                //缓存Control
                .cacheControl(executionInput.getCacheControl())
                //local
                .locale(executionInput.getLocale())
                .build();
        //执行instrument：可以修改执行上下文
        InstrumentationExecutionParameters executionParameters = new InstrumentationExecutionParameters(executionInput, graphQLSchema, instrumentationState);
        executionContext = instrumentation.instrumentExecutionContext(executionContext, executionParameters);

        return executeOperation(executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    /**
     * @param executionContext 执行上下文
     * @param root root对象、如果有的话、就是数据来源之一；
     * @param operationDefinition 操作定义
     * @return 查询执行结果
     */
    private CompletableFuture<ExecutionResult> executeOperation(ExecutionContext executionContext,Object root, OperationDefinition operationDefinition) {

        /**
         * 使用执行上下文instrument结果
         */
        InstrumentationExecuteOperationParameters executeOperationParameters = new InstrumentationExecuteOperationParameters(executionContext);
        InstrumentationContext<ExecutionResult> executeOperationCtx = instrumentation.beginExecuteOperation(executeOperationParameters);

        GraphQLObjectType operationRootType;
        try {
            //fixme query对象的定义和包含的字段
            operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                ExecutionResult executionResult = new ExecutionResultImpl(Collections.singletonList((GraphQLError) rte));
                CompletableFuture<ExecutionResult> resultCompletableFuture = completedFuture(executionResult);

                executeOperationCtx.onDispatched(resultCompletableFuture);
                executeOperationCtx.onCompleted(executionResult, rte);
                return resultCompletableFuture;
            }
            throw rte;
        }

        /**
         * fixme 收集字段
         *      构造字段收集上下文；
         *      收集字段，包括include和skip的逻辑。
         *
         */
        //字段收集上下文
        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        //收集本次需要查询的字段，包括include和skip的逻辑
        MergedSelectionSet fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());


        //fixme 所有的查询都以此路径为起点、空串：""
        ExecutionPath rootPath = ExecutionPath.rootPath();
        /**
         * fixme
         *      operationRootType就是查询的入口类型，
         *              其fieldDefinitionsByName包含了该类型下的所有字段名称和字段类型定义GraphQLFieldDefinition
         *      rootPath 是空串""
         */
        ExecutionStepInfo executionStepInfo = newExecutionStepInfo().type(operationRootType).path(rootPath).build();
        //如果类型定义一个字段必须是非空的、而其是空的，则抛异常NonNullableFieldWasNullException、并且返回值data是null
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        //查询最终返回值
        CompletableFuture<ExecutionResult> result;
        try {
            //去掉了操作类型的判断，毕竟一般用来查询：if(operation == OperationDefinition.Operation.MUTATION)
            ExecutionStrategy executionStrategy = executionContext.getQueryStrategy();
            /**
             * fixme 使用策略执行查询，执行策略包括如下数据：
             *          ValuesResolver valuesResolver//值强转
             *          ExecutionStepInfoFactory executionStepInfoFactory; //包含值强转
             *          FieldCollector fieldCollector//字段收集器
             *          ResolveType resolvedType; //判断值的类型？
             *          DataFetcherExceptionHandler dataFetcherExceptionHandler;//dataFetcher异常处理器
             *
             *fixme 执行上下文：包含执行的很多东西
             */

            /**
             * 构造实行策略参数
             */
            ExecutionStrategyParameters strategyParameters = newParameters()
                    .executionStepInfo(executionStepInfo) //对定位查询字段对应的类型很重要
                    .source(root)
                    .localContext(null) // this is important to default as this
                    .fields(fields)
                    .nonNullFieldValidator(nonNullableFieldValidator)
                    .path(rootPath)
                    .build();
            //todo very import
            result = executionStrategy.execute(executionContext, strategyParameters);
        } catch (NonNullableFieldWasNullException e) {
            // 如果从请求的根源到字段错误的源的所有字段都返回Non-Null类型，则响应中的“数据”条目应为null。
            // http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
            result = completedFuture(new ExecutionResultImpl(null, executionContext.getErrors()));
        }

        executeOperationCtx.onDispatched(result);
        result = result.whenComplete(executeOperationCtx::onCompleted);
        return deferSupport(executionContext, result);
    }

    /*
     * 如果查询定义了延迟操作、则在此处为查询添加延迟结果发布器——这也是执行延迟代码的最佳时机。
     * Adds the deferred publisher if its needed at the end of the query.  This is also a good time for the deferred code to start running
     */
    private CompletableFuture<ExecutionResult> deferSupport(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result) {
        return result.thenApply(er -> {
            DeferSupport deferSupport = executionContext.getDeferSupport();
            if (deferSupport.isDeferDetected()) {
                // we start the rest of the query now to maximize throughput.  We have the initial important results
                // and now we can start the rest of the calls as early as possible (even before some one subscribes)
                Publisher<DeferredExecutionResult> publisher = deferSupport.startDeferredCalls();
                return ExecutionResultImpl.newExecutionResult().from(er)
                        .addExtension(GraphQL.DEFERRED_RESULTS, publisher)
                        .build();
            }
            return er;
        });

    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        OperationDefinition.Operation operation = operationDefinition.getOperation();
        if (operation == MUTATION) {
            GraphQLObjectType mutationType = graphQLSchema.getMutationType();
            if (mutationType == null) {
                throw new MissingRootTypeException("Schema is not configured for mutations.", operationDefinition.getSourceLocation());
            }
            return mutationType;
        } else if (operation == QUERY) {
            GraphQLObjectType queryType = graphQLSchema.getQueryType();
            if (queryType == null) {
                throw new MissingRootTypeException("Schema does not define the required query root type.", operationDefinition.getSourceLocation());
            }
            return queryType;
        } else if (operation == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
            if (subscriptionType == null) {
                throw new MissingRootTypeException("Schema is not configured for subscriptions.", operationDefinition.getSourceLocation());
            }
            return subscriptionType;
        } else {
            return assertShouldNeverHappen("Unhandled case.  An extra operation enum has been added without code support");
        }
    }
}
