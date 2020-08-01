package graphql.execution;


import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
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
    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(Execution.class);

    //FieldCollector -> ConditionalNodes 包含skip、include指令逻辑
    private final FieldCollector fieldCollector = new FieldCollector();

    // ValuesResolver：强转变量和强转参数的实现
    // https://spec.graphql.org/draft/#sec-Coercing-Variable-Values
    // https://spec.graphql.org/draft/#sec-Coercing-Field-Arguments
    private final ValuesResolver valuesResolver = new ValuesResolver();

    //执行策略
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;

    //动态机制
    private final Instrumentation instrumentation;

    //解析dataFetcher返回的数据
    private ValueUnboxer valueUnboxer;

    public Execution(ExecutionStrategy queryStrategy,
                     ExecutionStrategy mutationStrategy,
                     ExecutionStrategy subscriptionStrategy,
                     Instrumentation instrumentation,
                     ValueUnboxer valueUnboxer) {
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new AsyncExecutionStrategy();
        this.instrumentation = instrumentation;
        this.valueUnboxer = valueUnboxer;
    }


    public CompletableFuture<ExecutionResult> execute(Document document, //查询文档
                                                      GraphQLSchema graphQLSchema, //schema
                                                      ExecutionId executionId, //inputId
                                                      ExecutionInput executionInput, //请求数据：变量、上下文等
                                                      //全局 InstrumentationState，保存各种请求状态使用
                                                      InstrumentationState instrumentationState) {

        //GetOperationResult保存有操作定义、和所有命名片段定义map，是一个container
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;
        Map<String, Object> inputVariables = executionInput.getVariables();

        //查询变量：名称、类型(注意，只有List、NonNull和TypeName三种)、默认值和变量指令
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

        Map<String, Object> coercedVariables;
        try {
            //https://spec.graphql.org/draft/#sec-Coercing-Variable-Values
            coercedVariables = valuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, inputVariables);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            throw rte;
        }

        ExecutionContext executionContext = newExecutionContextBuilder()
                .instrumentation(instrumentation)
                .instrumentationState(instrumentationState)
                .executionId(executionId)
                .graphQLSchema(graphQLSchema)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
                .context(executionInput.getContext())
                .localContext(executionInput.getLocalContext())
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .variables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
                .cacheControl(executionInput.getCacheControl())
                .locale(executionInput.getLocale())
                .valueUnboxer(valueUnboxer)
                .executionInput(executionInput)
                .build();


        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(
                executionInput, graphQLSchema, instrumentationState
        );
        executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters);
        return executeOperation(executionContext, parameters, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private CompletableFuture<ExecutionResult> executeOperation(ExecutionContext executionContext, InstrumentationExecutionParameters instrumentationExecutionParameters
            , Object root, OperationDefinition operationDefinition) {

        InstrumentationExecuteOperationParameters instrumentationParams = new InstrumentationExecuteOperationParameters(executionContext);
        InstrumentationContext<ExecutionResult> executeOperationCtx = instrumentation.beginExecuteOperation(instrumentationParams);

        OperationDefinition.Operation operation = operationDefinition.getOperation();
        GraphQLObjectType operationRootType;

        try {
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

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();


        /**收集要获取的字段：包括对片段的分析，结果对象是有一个map属性。
         * 该层的 Selection k-v集合，k是该层的字段名字，value是名字对应的MergedField
         *  MergedField 包含 List<Field>、字段名称、字段别名、第一个Field对象等信息
         *      具体不同的字段、参数、指令不同，可在list中获取到
         */
        MergedSelectionSet mergedSelectionSet = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());

        //根字段，segment和parent都是null
        ResultPath path = ResultPath.rootPath();
        //开始的stepInfo：类型肯定是查询类型，path的segment和parent就是null
        //但是开始请求第一个字段的时候就不是了
        ExecutionStepInfo executionStepInfo =
                newExecutionStepInfo()
                        .type(operationRootType)
                        .path(path).build();
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        //获取策略上下文
        ExecutionStrategyParameters strategyParameters = newParameters()
                //path的segment、parent都是null，type是查询类型
                .executionStepInfo(executionStepInfo)
                //input里边的root属性
                .source(root)
                //input里边的localContext属性
                .localContext(executionContext.getLocalContext())
                //该层需要获取的字段、按照别名分组
                .fields(mergedSelectionSet)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(path)
                .build();

        CompletableFuture<ExecutionResult> result;
        try {
            ExecutionStrategy executionStrategy;
            if (operation == OperationDefinition.Operation.MUTATION) {
                executionStrategy = executionContext.getMutationStrategy();
            } else if (operation == SUBSCRIPTION) {
                executionStrategy = executionContext.getSubscriptionStrategy();
            } else {
                executionStrategy = executionContext.getQueryStrategy();
            }
            if (logNotSafe.isDebugEnabled()) {
                logNotSafe.debug("Executing '{}' query operation: '{}' using '{}' execution strategy", executionContext.getExecutionId(), operation, executionStrategy.getClass().getName());
            }
            result = executionStrategy.execute(executionContext, strategyParameters);
        } catch (NonNullableFieldWasNullException e) {
            // this means it was non null types all the way from an offending non null type
            // up to the root object type and there was a a null value some where.
            //
            // The spec says we should return null for the data in this case
            //
            // http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
            //
            // Returns a new CompletableFuture that is already completed with the given value.
            result = completedFuture(new ExecutionResultImpl(null, executionContext.getErrors()));
        }

        // note this happens NOW - not when the result completes
        executeOperationCtx.onDispatched(result);

        result = result.whenComplete(executeOperationCtx::onCompleted);

        return result;
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
