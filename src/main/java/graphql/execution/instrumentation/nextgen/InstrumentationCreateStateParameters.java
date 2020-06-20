package graphql.execution.instrumentation.nextgen;

import graphql.execution.ExecutionInput;
import graphql.masker.Internal;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.nextgen.Instrumentation} methods
 */
@Internal
public class InstrumentationCreateStateParameters {
    private final GraphQLSchema schema;
    private final ExecutionInput executionInput;

    public InstrumentationCreateStateParameters(GraphQLSchema schema, ExecutionInput executionInput) {
        this.schema = schema;
        this.executionInput = executionInput;
    }

    public GraphQLSchema getSchema() {
        return schema;
    }

    public ExecutionInput getExecutionInput() {
        return executionInput;
    }
}
