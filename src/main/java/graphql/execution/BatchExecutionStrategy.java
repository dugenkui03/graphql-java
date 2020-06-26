package graphql.execution;

import graphql.ExecutionResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;

/**
 * 并发调用自定义的fetcher
 */
public class BatchExecutionStrategy extends ExecutionStrategy {


    /**
     * fixme: 在completeValueForObject中被递归调用
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return
     * @throws NonNullableFieldWasNullException
     */
    @Override
    public CompletableFuture<ExecutionResult> execute(
            ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        CompletableFuture<ExecutionResult> result = new CompletableFuture<>();

        /**
         * 最顶层节点的三个字段
         */
        MergedSelectionSet selectionSet = parameters.getFields();


        Set<String> currentLevFields = selectionSet.keySet();

        List<CompletableFuture> fieldResultEle = new ArrayList<>();
        for (String fieldName : currentLevFields) {
            //获取当前顶级字段
            MergedField currentField = selectionSet.getSubField(fieldName);

            //获取当前顶级字段的名字或别名
            String nameForPath = mkNameForPath(currentField);

            //当前当前顶级字段的结果路径
            ResultPath newResultPath = parameters.getPath().segment(nameForPath);

            //获取一个新的执行策略参数：当前字段、当前字段结果路径、父字段执行策略参数
            ExecutionStrategyParameters newStrategyParameters = parameters.transform(builder -> builder.field(currentField).path(newResultPath).parent(parameters));

            //这个fetcher的结果
            CompletableFuture<?> completableFuture = fetcerhCal(executionContext, newStrategyParameters);
            fieldResultEle.add(completableFuture);
            //递归调用
        }


        return null;
    }

    /**
     * fixme 最原子的计算：所有自定的fetcher都需要调用这个方法、并发获取结果
     */
    CompletableFuture<?> fetcerhCal(ExecutionContext executionContext, ExecutionStrategyParameters strategyParameters) {
        GraphQLObjectType parentType = (GraphQLObjectType) strategyParameters.getExecutionStepInfo().getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDefinition = getFieldDef(executionContext.getGraphQLSchema(), parentType, strategyParameters.getField().getSingleField());

        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parentType, fieldDefinition);


        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(codeRegistry, fieldDefinition.getArguments(), strategyParameters.getField().getArguments(), executionContext.getVariables());
        ExecutionStepInfo executionStepInfo = createExecutionStepInfo(executionContext, strategyParameters, fieldDefinition, parentType);
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldDefinition.getType(), strategyParameters.getField());


        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(strategyParameters.getSource())
                .localContext(strategyParameters.getLocalContext())
                .arguments(argumentValues)
                .fieldDefinition(fieldDefinition)
                .mergedField(strategyParameters.getField())
                .fieldType(fieldDefinition.getType())
                .executionStepInfo(executionStepInfo)
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();


        return CompletableFuture.supplyAsync(() -> {
            try {
                return dataFetcher.get(environment);
            } catch (Exception e) {
            }
            return null;
        });
    }



}
