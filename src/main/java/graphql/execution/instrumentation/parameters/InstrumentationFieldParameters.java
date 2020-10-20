package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLFieldDefinition;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link Instrumentation} methods
 *
 * fixme Instrumentation 方法使用的参数类、仅仅 beginField 使用了。
 */
@PublicApi
public class InstrumentationFieldParameters {

    // 调用上下文
    private final ExecutionContext executionContext;

    // 当前字段的详细信息，包括输出类型、定义类型、字段定义、参数父子段信息
    private final Supplier<ExecutionStepInfo> executionStepInfo;

    // 全局状态类
    private final InstrumentationState instrumentationState;

    public InstrumentationFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo) {
        this(executionContext, executionStepInfo, executionContext.getInstrumentationState());
    }

    InstrumentationFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.executionStepInfo = executionStepInfo;
        this.instrumentationState = instrumentationState;
    }

    /**
     * Returns a cloned parameters object with the new state
     * 创建一个克隆的对象：引用的对象是一样的。
     *
     * @param instrumentationState the new state for this parameters object
     * @return a new parameters object with the new state
     */
    public InstrumentationFieldParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationFieldParameters(
                this.executionContext, this.executionStepInfo, instrumentationState);
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public GraphQLFieldDefinition getField() {
        return executionStepInfo.get().getFieldDefinition();
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo.get();
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
