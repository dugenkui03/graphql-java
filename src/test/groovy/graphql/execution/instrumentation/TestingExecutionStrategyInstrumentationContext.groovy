package graphql.execution.instrumentation

import graphql.execution.ExecutionResult

class TestingExecutionStrategyInstrumentationContext extends TestingInstrumentContext<ExecutionResult> implements ExecutionStrategyInstrumentationContext {

    TestingExecutionStrategyInstrumentationContext(Object op, Object executionList, Object throwableList) {
        super(op, executionList, throwableList)
    }
}

