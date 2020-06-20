package graphql.execution.strategy;

import graphql.execution.ExecutionResult;
import graphql.execution.exception.handler.DataFetcherExceptionHandler;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.exception.NonNullableFieldWasNullException;
import graphql.execution.exception.handler.SimpleDataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.utils.AsyncUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async non-blocking execution, but serial: only one field at the the time will be resolved.
 * See {@link AsyncExecutionStrategy} for a non serial (parallel) execution of every field.
 */
public class AsyncSerialExecutionStrategy extends AbstractAsyncExecutionStrategy {

    public AsyncSerialExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    public AsyncSerialExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings({"TypeParameterUnusedInFormals","FutureReturnValueIgnored"})
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        InstrumentationContext<ExecutionResult> executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);
        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());

        CompletableFuture<List<ExecutionResult>> resultsFuture = AsyncUtil.eachSequentially(fieldNames, (fieldName, index, prevResults) -> {
            MergedField currentField = fields.getSubField(fieldName);
            ExecutionPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));
            return resolveField(executionContext, newParameters);
        });

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        resultsFuture.whenComplete(handleResults(executionContext, fieldNames, overallResult));
        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

}
