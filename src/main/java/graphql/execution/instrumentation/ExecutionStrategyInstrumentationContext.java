package graphql.execution.instrumentation;

import graphql.execution.ExecutionResult;
import graphql.execution.FieldValueInfo;
import graphql.execution.MergedField;

import java.util.List;

public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) { }

    //deferredField回调函数
    default void onDeferredField(MergedField field) { }
}
