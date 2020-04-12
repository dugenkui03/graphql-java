package graphql.execution.defer;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.ExecutionStrategyParameters;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 捕获延迟调用时产生的错误。
 *
 * This captures errors that occur while a deferred call is being made
 */
@Internal
public class DeferredErrorSupport {
    //所谓的写入时复制、即没有写入，则所有的调用者访问的时同一个对象
    private final List<GraphQLError> errors = new CopyOnWriteArrayList<>();

    //捕获dataFetcher抛出的异常
    public void onFetchingException(ExecutionStrategyParameters parameters, Throwable e) {
        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(parameters.getPath(), e, parameters.getField().getSingleField().getSourceLocation());
        onError(error);
    }

    //添加异常信息
    public void onError(GraphQLError gError) {
        errors.add(gError);
    }

    //获取异常信息
    public List<GraphQLError> getErrors() {
        return errors;
    }
}
