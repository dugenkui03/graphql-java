package graphql.util;

import graphql.masker.Internal;

@Internal
public class TraverserResult {

    //累加的结果
    private final Object accumulatedResult;

    //使用指定的累加的结果创建遍历结果
    public TraverserResult(Object accumulatedResult) {
        this.accumulatedResult = accumulatedResult;
    }

    //获取累加的结果
    public Object getAccumulatedResult() {
        return accumulatedResult;
    }

}
