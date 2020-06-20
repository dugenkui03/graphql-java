package graphql.execution.instrumentation.nextgen;

import graphql.execution.ExecutionInput;
import graphql.masker.Internal;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.nextgen.Instrumentation} methods
 */
@Internal
public class InstrumentationValidationParameters extends InstrumentationExecutionParameters {
    private final Document document;

    public InstrumentationValidationParameters(ExecutionInput executionInput, Document document, GraphQLSchema schema, InstrumentationState instrumentationState) {
        super(executionInput, schema, instrumentationState);
        this.document = document;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    @Override
    public InstrumentationValidationParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationValidationParameters(
                this.getExecutionInput(), document, getSchema(), instrumentationState);
    }


    public Document getDocument() {
        return document;
    }
}
