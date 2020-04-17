package graphql.execution.instrumentation;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
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
import graphql.schema.PropertyDataFetcher;
import graphql.validation.ValidationError;
import org.omg.CORBA.TIMEOUT;

import java.util.List;
import java.util.concurrent.*;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;

/**
 * fixme: instrument方法分两种 (前者做记录、后者直接修改值？)TODO 汇总一下
 *        一种是返回值为InstrumentationContext的，和onDispatched和onComplete一块用，
 *        这两个方法参数没有上下文，但是生成InstrumentationContext的方法有上下文，因此定义方法时仍然能够使用上下文；非常重要；
 *        一种是直接修改值，简单粗暴；
 *
 * Provides the capability to instrument the execution steps of a GraphQL query.
 *
 * For example you might want to track which fields are taking the most time to fetch from the backing database
 * or log what fields are being asked for.
 *
 * Remember that graphql calls can cross threads so make sure you think about the thread safety of any instrumentation
 * code when you are writing it.
 *
 * Each step gives back an {@link graphql.execution.instrumentation.InstrumentationContext} object.  This has two callbacks on it,
 * one for the step is `dispatched` and one for when the step has `completed`.  This is done because many of the "steps" are asynchronous
 * operations such as fetching data and resolving it into objects.
 */
public interface Instrumentation {

    /**
     * This will be called just before execution to create an object that is given back to all instrumentation methods
     * to allow them to have per execution request state
     *
     * @return a state object that is passed to each method
     */
    default InstrumentationState createState() {
        return null;
    }

    /**
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
     * 在查询执行前
     * This is called right at the start of query execution and its the first step in the instrumentation chain.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before a query is parsed.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters);

    /**
     * This is called just before the parsed query document is validated.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters);

    /**
     *
     * This is called just before the execution of the query operation is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters);

    /**
     * This is called each time an {@link graphql.execution.ExecutionStrategy} is invoked, which may be multiple times
     * per query as the engine recursively descends down over the query.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters);

    /**
     * This is called just before a deferred field is resolved into a value.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters);

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
     * @param parameters the parameters to this step fixme 包含 fetchedValue
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * This is called just before the complete field list is started.
     *
     * @param parameters the parameters to this step
     *
     * @return a non null {@link InstrumentationContext} object that will be called back when the step ends
     */
    default InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return noOp();
    }

    /**
     * 在入参进行解析、验证和执行前，对其进行调整并返回：上下文包含schema和InstrumentationState
     *
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
     * 在使用Document对象和变量之前、允许你对他们进行调整
     *
     * This is called to instrument a {@link graphql.language.Document} and variables before it is used allowing you to adjust the query AST if you so desire
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
     * 在schema进行解析、验证和查询前进行instrument。schema对象主要包括 代码生成器、自定义类型、schema定义和扩展定义等
     * This is called to instrument a {@link graphql.schema.GraphQLSchema} before it is used to parse, validate
     * and execute a query, allowing you to adjust what types are used.
     *
     *         private GraphQLObjectType queryType;  mutationType;  subscriptionType;
     *         private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
     *         private Set<GraphQLType> additionalTypes = new LinkedHashSet<>();
     *         private SchemaDefinition definition;
     *         private List<SchemaExtensionDefinition> extensionDefinitions;
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
     * @param executionParameters       the parameters describing the field to be fetched
     *
     * @return a non null instrumented ExecutionContext, the default is to return to the same object
     */
    default ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters executionParameters) {
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
//        return dataFetcher;

        //有超时的fetcher
        if(parameters.isTrivialDataFetcher()){
            return dataFetcher;
        }else{
           return fetchingEnvironment->{
               Callable task=()-> dataFetcher.get(parameters.getEnvironment());
                   return ForkJoinPool.commonPool().submit(task).get(200,TimeUnit.MILLISECONDS);
           };
        }
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
        return CompletableFuture.completedFuture(executionResult);
    }

}
