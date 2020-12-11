package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.PublicSpi;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * 提供了在GraphQL各个步骤进行instrument的能力。比如、你可能想跟踪获取哪个 字段/实体 的时候花了更多的时间，或者记录请求了什么字段。
 * fixme： 切记graphql执行是跨线程的，因此需要注意instrumentation是否需要保证线程安全。
 *
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 *
 * Remember that graphql calls can cross threads so make sure you think about the thread safety of any instrumentation
 * code when you are writing it.
 *
 * fixme 每一个instrumentation方法都返回了一个InstrumentationContext对象、其包含了两个回调方法：dispatched和completed。
 * Each step gives back an {@link graphql.execution.instrumentation.InstrumentationContext} object.  This has two callbacks on it,
 * one for the step is `dispatched` and one for when the step has `completed`.  This is done because many of the "steps" are asynchronous
 * operations such as fetching data and resolving it into objects.
 */
@PublicSpi
public interface Instrumentation {

    /** 创建一个 有状态的对象，记录执行信息。
     *
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、、
     * @return a state object that is passed to each method
     */
    default InstrumentationState createState() {
        return null;
    }

    /**
     * fixme 使用schema和executeInput创建instrument的 状态记录对象。
     *
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @param parameters the parameters to this step
     *
     * @return a state object that is passed to each method
     */
    default InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return createState();
    }

    /**
     * fixme 处理最后结果、不处理中间过程，见GraphQl.executeAsync方法
     * This is called right at the start of query execution and its the first step in the instrumentation chain.
     *
     * @param parameters the parameters to this step
     *
     * @return fixme 回调方法、泛形为每次最终输出结果接口。
     */
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters);

    /**
     * fixme 回调方法在开始、结束解析文档的时候执行、可以修改文档内容、修改输入变量等。
     * This is called just before a query is parsed.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters);

    /**
     * fixme 在解析的文档在验证的时候执行、可以对验证错误进行记录。
     * This is called just before the parsed query document is validated.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters);

    /**
     * fixme 对最终的结果或者异常进行处理？详情见Execution
     * This is called just before the execution of the query operation is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters);

    /**fixme:
     *      1. 在ExecutionStrategyInstrumentationContext中对回调方法进行了拓展；
     *      2.
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends down over the query.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters);


    /**
     * This is called each time a subscription field produces a new reactive stream event value
     * and it needs to be mapped over via the graphql field subselection.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before a field is resolved into a value.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters);

    /**
     * This is called just before a field {@link DataFetcher} is invoked.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters);


    /**
     * This is called just before the complete field is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the complete field list is started.
     * fixme 可以对list进行操作
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * This is called to instrument a {@link graphql.ExecutionInput} before it is used to parse, validate
     * and execute a query, allowing you to adjust what query input parameters are used
     *
     * @param executionInput the execution input to be used
     * @param parameters     the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionInput, the default is to return to the same object
     */
    default ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        return executionInput;
    }

    /**
     * This is called to instrument a {@link graphql.language.Document} and variables
     * before it is used allowing you to adjust the query AST if you so desire
     *
     * fixme
     *      修改文档和变量：
     *          1. 如果有的字段使用@include不能去掉，可以在这里搞；
     *          2. 可以根据变量指令，修改输入变量，例如加上当前时间戳；
     *          3. todo：没有修改arugment的方法，可以加；
     *
     * @param documentAndVariables the document and variables to be used
     * @param parameters           the parameters describing the execution
     *
     * @return a non null instrumented DocumentAndVariables, the default is to return to the same objects
     */
    default DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters) {
        return documentAndVariables;
    }

    /**
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     * @param schema     the schema to be used
     * @param parameters the parameters describing the field to be fetched
     *
     * @return a non null instrumented GraphQLSchema, the default is to return to the same object
     */
    default GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        return schema;
    }

    /**
     * This is called to instrument a {@link ExecutionContext} before it is used to execute a query,
     * allowing you to adjust the base data used.
     *
     * @param executionContext the execution context to be used
     * @param parameters       the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionContext, the default is to return to the same object
     */
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        return executionContext;
    }


    /**
     * This is called to instrument a {@link DataFetcher} just before it is used to fetch a field, allowing you
     * to adjust what information is passed back or record information about specific data fetches.  Note
     * the same data fetcher instance maybe presented to you many times and that data fetcher
     * implementations widely vary.
     *
     * @param dataFetcher the data fetcher about to be used
     * @param parameters  the parameters describing the field to be fetched
     *
     * @return a non null instrumented DataFetcher, the default is to return to the same object
     */
    default DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        return dataFetcher;
    }

    /**
     * This is called to allow instrumentation to instrument the execution result in some way
     *
     * @param executionResult {@link java.util.concurrent.CompletableFuture} of the result to instrument
     * @param parameters      the parameters to this step
     *
     * @return a new execution result completable future
     */
    default CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        // 指定对象为异步任务结果，默认不处理
        return CompletableFuture.completedFuture(executionResult);
    }

}
