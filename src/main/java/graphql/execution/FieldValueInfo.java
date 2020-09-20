package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;


/**
 * 对应执行策略中、某个字段的返回值
 */
@PublicApi
public class FieldValueInfo {

    //该字段类型：对象、集合、标量、空、枚举
    public enum CompleteValueType {
        OBJECT,
        LIST,
        NULL,
        SCALAR,
        ENUM
    }

    //结果类型
    private final CompleteValueType completeValueType;

    //fixme 某层调用策略类execute后执行的结果
    private final CompletableFuture<ExecutionResult> fieldValue;
    /**
     * 在 {@link ExecutionStrategy#completeValueForList} 中被赋值
     *
     * 如果是list类型的返回值，里边保存了每一个元素的结果
     */
    private final List<FieldValueInfo> fieldValueInfos;

    private FieldValueInfo(CompleteValueType completeValueType, CompletableFuture<ExecutionResult> fieldValue
            , List<FieldValueInfo> fieldValueInfos) {
        assertNotNull(fieldValueInfos, () -> "fieldValueInfos can't be null");
        this.completeValueType = completeValueType;
        this.fieldValue = fieldValue;
        this.fieldValueInfos = fieldValueInfos;
    }

    public CompleteValueType getCompleteValueType() {
        return completeValueType;
    }

    public CompletableFuture<ExecutionResult> getFieldValue() {
        return fieldValue;
    }

    public List<FieldValueInfo> getFieldValueInfos() {
        return fieldValueInfos;
    }

    public static Builder newFieldValueInfo(CompleteValueType completeValueType) {
        return new Builder(completeValueType);
    }

    @Override
    public String toString() {
        return "FieldValueInfo{" +
                "completeValueType=" + completeValueType +
                ", fieldValue=" + fieldValue +
                ", fieldValueInfos=" + fieldValueInfos +
                '}';
    }

    @SuppressWarnings("unused")
    public static class Builder {
        private CompleteValueType completeValueType;
        private CompletableFuture<ExecutionResult> executionResultFuture;
        private List<FieldValueInfo> listInfos = new ArrayList<>();

        public Builder(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
        }

        public Builder completeValueType(CompleteValueType completeValueType) {
            this.completeValueType = completeValueType;
            return this;
        }

        //针对null、标量、枚举和对象赋值
        //list中也会为整个
        public Builder fieldValue(CompletableFuture<ExecutionResult> executionResultFuture) {
            this.executionResultFuture = executionResultFuture;
            return this;
        }

        //在 {@link ExecutionStrategy#completeValueForList} 中被调用
        public Builder fieldValueInfos(List<FieldValueInfo> listInfos) {
            assertNotNull(listInfos, () -> "fieldValueInfos can't be null");
            this.listInfos = listInfos;
            return this;
        }

        public FieldValueInfo build() {
            return new FieldValueInfo(completeValueType, executionResultFuture, listInfos);
        }
    }
}