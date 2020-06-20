package graphql.execution.nextgen;

import graphql.execution.ExecutionInput;
import graphql.execution.ExecutionResult;
import graphql.execution.ExecutionResultImpl;
import graphql.error.GraphQLError;
import graphql.masker.Internal;
import graphql.execution.utils.AsyncUtil;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.concurrent.CompletableFuture;

@Internal
public class Execution {

    ExecutionHelper executionHelper = new ExecutionHelper();

    public CompletableFuture<ExecutionResult> execute(ExecutionStrategy executionStrategy,
                                                      Document document,
                                                      GraphQLSchema graphQLSchema,
                                                      ExecutionId executionId,
                                                      ExecutionInput executionInput,
                                                      InstrumentationState instrumentationState) {
        ExecutionHelper.ExecutionData executionData;
        try {
            executionData = executionHelper.createExecutionData(document, graphQLSchema, executionId, executionInput, instrumentationState);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            return AsyncUtil.exceptionallyCompletedFuture(rte);
        }

        try {
            return executionStrategy
                    .execute(executionData.executionContext, executionData.fieldSubSelection)
                    .thenApply(ResultNodesUtil::toExecutionResult);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
