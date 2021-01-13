package graphql;


/**
 * All the errors in graphql belong to one of these categories
 */
@PublicApi
public enum ErrorType implements ErrorClassification {
    InvalidSyntax,  // 无效语法
    ValidationError, // 验证错误
    DataFetchingException, // DF异常
    NullValueInNonNullableField, // 非空字段变为非空
    OperationNotSupported, // 不支持操作
    ExecutionAborted // 执行中断
}
