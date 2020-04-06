package graphql.execution;

/**
 * A provider of {@link ExecutionId}s
 */
public interface ExecutionIdProvider {

    //默认的id生成器：查询dsl、查询名称、查询上下文，在默认的uuid生成器里边都没有用到
    ExecutionIdProvider DEFAULT_EXECUTION_ID_PROVIDER = (query, operationName, context) -> ExecutionId.generate();


    /**
     * 执行id可以包含query、执行名称和执行上下文等信息，使得id更加具有业务含义
     * Allows provision of a unique identifier per query execution.
     *
     * @param query         the query to be executed
     * @param operationName thr name of the operation
     * @param context       the context object passed to the query
     *
     * @return a non null {@link ExecutionId}
     */
    ExecutionId provide(String query, String operationName, Object context);
}
