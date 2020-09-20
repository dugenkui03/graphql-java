package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.FieldValueInfo;

import java.util.List;

@PublicSpi
public interface ExecutionStrategyInstrumentationContext extends InstrumentationContext<ExecutionResult> {

    /**
     * 在策略类的execute()中调用
     *
     * @param fieldValueInfoList 当前层级每个字段的解析结果
     */
    default void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) { }

}
