package graphql;

import graphql.error.AssertException;
import graphql.error.GraphQLException;
import graphql.execution.*;
import graphql.execution.exception.AbortExecutionException;
import graphql.execution.exception.UnresolvedTypeException;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.strategy.AsyncExecutionStrategy;
import graphql.execution.strategy.AsyncSerialExecutionStrategy;
import graphql.execution.strategy.ExecutionStrategy;
import graphql.execution.strategy.SubscriptionExecutionStrategy;
import graphql.language.Document;
import graphql.language.ParseResult;
import graphql.masker.Internal;
import graphql.masker.PublicApi;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.DocumentParser;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.InvalidSchemaException;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static graphql.util.Assert.assertNotNull;
import static graphql.execution.ExecutionIdProvider.DEFAULT_EXECUTION_ID_PROVIDER;
import static graphql.execution.instrumentation.DocumentAndVariables.newDocumentAndVariables;

/**
 * fixme:
 *      查询开始的位置
 *
 * fixme:
 *      graphql类
 *
 * This class is where all graphql-java query execution begins.  It combines the objects that are needed
 * to make a successful graphql query, with the most important being the {@link graphql.schema.GraphQLSchema schema}
 * and the {@link ExecutionStrategy execution strategy}
 *
 * Building this object is very cheap and can be done on each execution if necessary.  Building the schema is often not
 * as cheap, especially if its parsed from graphql IDL schema format via {@link graphql.schema.idl.SchemaParser}.
 *
 * The data for a query is returned via {@link ExecutionResult#getData()} and any errors encountered as placed in
 * {@link ExecutionResult#getErrors()}.
 *
 * <h2>Runtime Exceptions</h2>
 *
 * Runtime exceptions can be thrown by the graphql engine if certain situations are encountered.  These are not errors
 * in execution but rather totally unacceptable conditions in which to execute a graphql query.
 * <ul>
 * <li>{@link graphql.schema.CoercingSerializeException} - is thrown when a value cannot be serialised by a Scalar type, for example
 * a String value being coerced as an Int.
 * </li>
 *
 * <li>{@link UnresolvedTypeException} - is thrown if a {@link graphql.schema.TypeResolver} fails to provide a concrete
 * object type given a interface or union type.
 * </li>
 *
 * <li>{@link InvalidSchemaException} - is thrown if the schema is not valid when built via
 * {@link graphql.schema.GraphQLSchema.Builder#build()}
 * </li>
 *
 * <li>{@link GraphQLException} - is thrown as a general purpose runtime exception, for example if the code cant
 * access a named field when examining a POJO.
 * </li>
 *
 * <li>{@link AssertException} - is thrown as a low level code assertion exception for truly unexpected code conditions
 * </li>
 *
 * </ul>
 */
@SuppressWarnings("Duplicates")
@PublicApi
public class GraphQL {

    /**
     * 当查询使用 @defer 指令的时候，这个字段是结果extension map中的一个key命中、对应的value是发送延迟结果的Publisher。代码在{@link Execution#deferSupport(ExecutionContext,CompletableFuture)}中可见：
     * <pre>
     * {@code
     *  ExecutionResultImpl.newExecutionResult().from(er)
     *                             .addExtension(GraphQL.DEFERRED_RESULTS, publisher)
     *                            .build()
     *  }</pre>
     * When @defer directives are used, this is the extension key name used to contain the {@link org.reactivestreams.Publisher} of deferred results
     */
    public static final String DEFERRED_RESULTS = "deferredResults";

    //构建的schema对象
    private final GraphQLSchema graphQLSchema;

    //查询策略：默认使用一步策略
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;

    //执行id生成器：默认随机的uuid，也可自定义，在uuid中包含上：String query, String operationName, Object context
    private final ExecutionIdProvider idProvider;

    /**
     * 默认使用dataLoaderInstrumentation fixme 如果异步执行、则instrument的状态必须是线程安全的
     *
     * 自定义的instrument即使是ChainedInstrumentation的、也要包含dataLoaderInstrumentation。
     * 逻辑在builder中的checkInstrumentationDefaultState()中
     */
    private final static Instrumentation DEFAULT_INSTRUMENTATION = new DataLoaderDispatcherInstrumentation();
    private final Instrumentation instrumentation;

    //预解析文档缓存类：可以指定解析文档的缓存key，推荐使用dsl
    private final PreparsedDocumentProvider preparsedDocumentProvider;

    /**
     * fixme
     *      dataFetcher值的 拆箱器，比如从Optional和DataFetcherResult中获取真正的业务值；
     *      他的作用就是、可以在dataFetcher中对返回值进行包装、然后使用此类进行拆箱。
     */
    private final ValueUnboxer valueUnboxer;



    /**
     * ===========================构造函数只能指定schema和执行策略，优先使用builder模式======================================
     */
    /**
     * fixme
     *      使用schema和自定义的ExecutionStrategy来构建查询对象
     * fixme
     *      ExecutionStrategy中也只有execute是抽象的，用于传递所查询的字段、返回这些字段的值，
     *      随着查询字段的深度、在ExecutionStrategy.completeValueForObject和Execution.executeOperation中都有被递归调用
     *      execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters)
     */

    //使用Schema构建查询对象
    @Internal
    @Deprecated
    public GraphQL(GraphQLSchema graphQLSchema) {
        //noinspection deprecation
        this(graphQLSchema, null, null);
    }

    @Internal
    @Deprecated
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        //noinspection deprecation
        this(graphQLSchema, queryStrategy, null);
    }

    /**
     * schema、查询执行策略、更新执行策略
     */
    @Internal
    @Deprecated
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, null, DEFAULT_EXECUTION_ID_PROVIDER, DEFAULT_INSTRUMENTATION, NoOpPreparsedDocumentProvider.INSTANCE, ValueUnboxer.DEFAULT);
    }

    /**
     * schema、查询执行策略、更新执行策略、订阅执行策略
     */
    @Internal
    @Deprecated
    public GraphQL(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy) {
        this(graphQLSchema, queryStrategy, mutationStrategy, subscriptionStrategy, DEFAULT_EXECUTION_ID_PROVIDER, DEFAULT_INSTRUMENTATION, NoOpPreparsedDocumentProvider.INSTANCE, ValueUnboxer.DEFAULT);
    }
    /**
     * ===========================end of "构造函数只能指定schema和执行策略，优先使用builder模式"======================================
     */


    /**
     * 在builder中被调用，几个构造函数快要被弃用了；
     *
     *      * fixme
     *      *      ExecutionStrategy中也只有execute是抽象的，用于传递所查询的字段、返回这些字段的值，
     *      *      随着查询字段的深度、在ExecutionStrategy.completeValueForObject和Execution.executeOperation中都有被递归调用
     *      *      execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters)
     */
    private GraphQL(GraphQLSchema graphQLSchema,
                    ExecutionStrategy queryStrategy,
                    ExecutionStrategy mutationStrategy,
                    ExecutionStrategy subscriptionStrategy,
                    ExecutionIdProvider idProvider,
                    Instrumentation instrumentation,
                    PreparsedDocumentProvider preparsedDocumentProvider,
                    ValueUnboxer valueUnboxer) {
        this.graphQLSchema = assertNotNull(graphQLSchema, "graphQLSchema must be non null");
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new SubscriptionExecutionStrategy();
        this.idProvider = assertNotNull(idProvider, "idProvider must be non null");
        this.instrumentation = assertNotNull(instrumentation);
        this.preparsedDocumentProvider = assertNotNull(preparsedDocumentProvider, "preparsedDocumentProvider must be non null");
        this.valueUnboxer = valueUnboxer;
    }

    //使用指定的schema构造builder对象
    public static Builder newGraphQL(GraphQLSchema graphQLSchema) {
        return new Builder(graphQLSchema);
    }

    /**
     * 基于现在的graphQL对象生成新的grahql对象，使用Consumer进行改变后返回。
     * 不同于GraphQL newGraphQL=this;该方法指向其属性的引用不同，这也是能够使用accept改变其属性、指向新的属性的原因之一、从而改变其行为的原因之一。
     *
     * This helps you transform the current GraphQL object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform fixme 修改函数
     *
     * @return a new GraphQL object based on calling build on that builder fixme 新的GraphQL对象
     */
    public GraphQL transform(Consumer<GraphQL.Builder> builderConsumer) {
        Builder builder = new Builder(this.graphQLSchema);
        builder.queryExecutionStrategy(nvl(this.queryStrategy, builder.queryExecutionStrategy))
                .mutationExecutionStrategy(nvl(this.mutationStrategy, builder.mutationExecutionStrategy))
                .subscriptionExecutionStrategy(nvl(this.subscriptionStrategy, builder.subscriptionExecutionStrategy))
                .executionIdProvider(nvl(this.idProvider, builder.idProvider))
                .instrumentation(nvl(this.instrumentation, builder.instrumentation))
                .preparsedDocumentProvider(nvl(this.preparsedDocumentProvider, builder.preparsedDocumentProvider));

        builderConsumer.accept(builder);

        return builder.build();
    }

    //obj为null、则返回elseObj
    private static <T> T nvl(T obj, T elseObj) {
        return obj == null ? elseObj : obj;
    }

    @PublicApi
    public static class Builder {
        //schema
        private GraphQLSchema graphQLSchema;
        //执行策略：默认使用执行策略
        private ExecutionStrategy queryExecutionStrategy = new AsyncExecutionStrategy();
        private ExecutionStrategy mutationExecutionStrategy = new AsyncSerialExecutionStrategy();
        private ExecutionStrategy subscriptionExecutionStrategy = new SubscriptionExecutionStrategy();

        //fixme 执行id生成器->可以作为某次执行的标志，可包含 查询dsl、查询名称和上下文信息等，比如在tracing中用到
        private ExecutionIdProvider idProvider = DEFAULT_EXECUTION_ID_PROVIDER;
        private Instrumentation instrumentation = null;
        private PreparsedDocumentProvider preparsedDocumentProvider = NoOpPreparsedDocumentProvider.INSTANCE;

        //是否不实用默认的Instrumentation
        private boolean doNotAddDefaultInstrumentations = false;

        //值解析器，不set则使用默认的值解析器
        private ValueUnboxer valueUnboxer = ValueUnboxer.DEFAULT;

        //schema必须是指定的，所以放在唯一构造函数中
        public Builder(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = graphQLSchema;
        }

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.graphQLSchema = assertNotNull(graphQLSchema, "GraphQLSchema must be non null");
            return this;
        }

        public Builder queryExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.queryExecutionStrategy = assertNotNull(executionStrategy, "Query ExecutionStrategy must be non null");
            return this;
        }

        public Builder mutationExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.mutationExecutionStrategy = assertNotNull(executionStrategy, "Mutation ExecutionStrategy must be non null");
            return this;
        }

        public Builder subscriptionExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.subscriptionExecutionStrategy = assertNotNull(executionStrategy, "Subscription ExecutionStrategy must be non null");
            return this;
        }

        public Builder instrumentation(Instrumentation instrumentation) {
            this.instrumentation = assertNotNull(instrumentation, "Instrumentation must be non null");
            return this;
        }

        public Builder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider) {
            this.preparsedDocumentProvider = assertNotNull(preparsedDocumentProvider, "PreparsedDocumentProvider must be non null");
            return this;
        }

        public Builder executionIdProvider(ExecutionIdProvider executionIdProvider) {
            this.idProvider = assertNotNull(executionIdProvider, "ExecutionIdProvider must be non null");
            return this;
        }

        /**
         * 出于性能的考虑，你不选择将 dataLoaderInstrumentation 放进graphql-instance；
         * For performance reasons you can 选择(opt into) situation where the default instrumentations (such
         * as {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation} will not be
         * automatically added into the graphql instance.
         * <p>
         *     大多数情况都不需要"不实用默认的Instrumentation"，除非你真正的突破了性能极限。
         * For most situations this is not needed unless you are really pushing the boundaries of performance
         * <p>
         * By default a certain graphql instrumentations will be added to the mix to more easily enable certain functionality.  This
         * allows you to stop this behavior
         *
         * @return this builder
         */
        public Builder doNotAddDefaultInstrumentations() {
            this.doNotAddDefaultInstrumentations = true;
            return this;
        }

        public Builder valueUnboxer(ValueUnboxer valueUnboxer) {
            this.valueUnboxer = valueUnboxer;
            return this;
        }

        public GraphQL build() {
            assertNotNull(graphQLSchema, "graphQLSchema must be non null");
            assertNotNull(queryExecutionStrategy, "queryStrategy must be non null");
            assertNotNull(idProvider, "idProvider must be non null");
            final Instrumentation augmentedInstrumentation = checkInstrumentationDefaultState(instrumentation, doNotAddDefaultInstrumentations);
            return new GraphQL(graphQLSchema, queryExecutionStrategy, mutationExecutionStrategy, subscriptionExecutionStrategy, idProvider, augmentedInstrumentation, preparsedDocumentProvider, valueUnboxer);
        }
    }


    //Executes the specified graphql query/mutation/subscription
    public ExecutionResult execute(String query) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .build();
        return execute(executionInput);
    }

    //指定提供给每个DataFetcher的上下文
    @Deprecated
    public ExecutionResult execute(String query, Object context) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .build();
        return execute(executionInput);
    }

    //指定查询名称和DataFetcher的上下文
    @Deprecated
    public ExecutionResult execute(String query, String operationName, Object context) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .build();
        return execute(executionInput);
    }

    //指定变量：Info: This sets context = root to be backwards compatible.
    @Deprecated
    public ExecutionResult execute(String query, Object context, Map<String, Object> variables) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .variables(variables)
                .build();
        return execute(executionInput);
    }

    //Info: This sets context = root to be backwards compatible.
    @Deprecated
    public ExecutionResult execute(String query, String operationName, Object context, Map<String, Object> variables) {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .root(context) // This we are doing do be backwards compatible
                .variables(variables)
                .build();
        return execute(executionInput);
    }

    //使用输入对象builder执行查询？为什么不直接使用输入对象，存在意义是啥
    public ExecutionResult execute(ExecutionInput.Builder executionInputBuilder) {
        return execute(executionInputBuilder.build());
    }

    //可以修改输入
    /**
     * Executes the graphql query using calling the builder function and giving it a new builder.
     * <p>
     * This allows a lambda style like :
     * <pre>
     * {@code
     *    ExecutionResult result = graphql.execute(input -> input.query("{hello}").root(startingObj).context(contextObj));
     * }
     * </pre>
     *
     * @param builderFunction a function that is given a {@link ExecutionInput.Builder}
     *
     * @return an {@link ExecutionResult} which can include errors
     */
    public ExecutionResult execute(UnaryOperator<ExecutionInput.Builder> builderFunction) {
        return execute(builderFunction.apply(ExecutionInput.newExecutionInput()).build());
    }

    //输入是ExecutionInput对象，调用了executeAsync
    public ExecutionResult execute(ExecutionInput executionInput) {
        try {
            //join和get的不同：join抛出的是运行时异常，get抛出的的是检查异常
            // :https://stackoverflow.com/questions/45490316/completablefuture-join-vs-get
            return executeAsync(executionInput).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    //异步执行，输入是ExecutionInput.Builder
    public CompletableFuture<ExecutionResult> executeAsync(ExecutionInput.Builder executionInputBuilder) {
        return executeAsync(executionInputBuilder.build());
    }

    //异步执行，输入可以修改ExecutionInput.Builder fixme func
    public CompletableFuture<ExecutionResult> executeAsync(UnaryOperator<ExecutionInput.Builder> builderFunction) {
        return executeAsync(builderFunction.apply(ExecutionInput.newExecutionInput()).build());
    }

    /**
     * fixme 所有的执行方法最终都会调用这个方法进行异步执行
     */
    public CompletableFuture<ExecutionResult> executeAsync(ExecutionInput executionInput) {
        try {
            //如果入参没有executionId，则生成
            executionInput = ensureInputHasId(executionInput);

            //生成使用的Instruemnt的 状态类
            InstrumentationState instrumentationState = instrumentation.createState(new InstrumentationCreateStateParameters(this.graphQLSchema, executionInput));

            //instrument的入参
            InstrumentationExecutionParameters inputInstrumentationParameters = new InstrumentationExecutionParameters(executionInput, this.graphQLSchema, instrumentationState);
            //构造输入 fixme：对入参的处理可以放到这里
            executionInput = instrumentation.instrumentExecutionInput(executionInput, inputInstrumentationParameters);
            InstrumentationExecutionParameters instrumentationParameters = new InstrumentationExecutionParameters(executionInput, this.graphQLSchema, instrumentationState);
            InstrumentationContext<ExecutionResult> executionInstrumentation = instrumentation.beginExecution(instrumentationParameters);

            //对schema进行修改
            GraphQLSchema graphQLSchema = instrumentation.instrumentSchema(this.graphQLSchema, instrumentationParameters);

            /**
             * fixme：重要
             *      1. 执行入口
             *      2. 入参是instrument状态持有器对象，因为全局变量instrumentation在那里都能调用
             */
            CompletableFuture<ExecutionResult> executionResult = parseValidateAndExecute(executionInput, graphQLSchema, instrumentationState);

            //todo 这是个什么意思：finish up instrumentation
            executionResult = executionResult.whenComplete(executionInstrumentation::onCompleted);

            // fixme 对执行结果的修改
            executionResult = executionResult.thenCompose(result -> instrumentation.instrumentExecutionResult(result, instrumentationParameters));
            return executionResult;
        } catch (AbortExecutionException abortException) {
            return CompletableFuture.completedFuture(abortException.toExecutionResult());
        }
    }

    //如果输入对象中没有 exeId ，则调用idProvider获取 exeId；
    private ExecutionInput ensureInputHasId(ExecutionInput executionInput) {
        if (executionInput.getExecutionId() != null) {
            return executionInput;
        }
        String queryString = executionInput.getQuery();
        String operationName = executionInput.getOperationName();
        Object context = executionInput.getContext();
        return executionInput.transform(builder -> builder.executionId(idProvider.provide(queryString, operationName, context)));
    }


    /**
     * 解析、验证、执行
     * @param executionInput    查询输入
     * @param graphQLSchema     schema
     * @param instrumentationState    Instrumentation状态记录器
     */
    private CompletableFuture<ExecutionResult> parseValidateAndExecute(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        //输入对象的原子引用
        AtomicReference<ExecutionInput> executionInputRef = new AtomicReference<>(executionInput);

        /**
         * fixme：是个函数 R apply(T t)
         *      解析和验证：只验证查询在schema上下文的合法性，变量的合法性验证在ValuesResolver.coerceVariableValues中
         */
        Function<ExecutionInput, PreparsedDocumentEntry> computeFunction = transformedInput -> {
            // if they change the original query in the pre-parser, then we want to see it downstream from then on
            executionInputRef.set(transformedInput);//为executionInput设置新的引用：
            return parseAndValidate(executionInputRef, graphQLSchema, instrumentationState);
        };

        //从缓存获取结果、没有缓存则使用computeFunction解析、验证后返回Document文档。默认不缓存NoOpPreparsedDocumentProvider；
        PreparsedDocumentEntry preparsedDoc = preparsedDocumentProvider.getDocument(executionInput, computeFunction);

        //解析阶段出错，dataPresent是false
        if (preparsedDoc.hasErrors()) {
            return CompletableFuture.completedFuture(new ExecutionResultImpl(preparsedDoc.getErrors()));
        }

        return execute(executionInputRef.get(), preparsedDoc.getDocument(), graphQLSchema, instrumentationState);
    }

    /**
     * 进行解析和验证，如果出错、则将结果保存在PreparsedDocumentEntry的list元素中；
     *      只有解析的文档才可以缓存、验证节点永远不会
     */
    private PreparsedDocumentEntry parseAndValidate(AtomicReference<ExecutionInput> executionInputRef, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        ExecutionInput executionInput = executionInputRef.get();
        String query = executionInput.getQuery();

        /**
         * fixme
         *    1. 解析
         */
        ParseResult parseResult = parse(executionInput, graphQLSchema, instrumentationState);
        //如果解析失败，则返回解析异常
        if (parseResult.isFailure()) {
            return new PreparsedDocumentEntry(parseResult.getException().toInvalidSyntaxError());
        } else {
            /**
             * fixme
             *      2. 如果解析成功，
             *           a)则instrument executionInput;
             *           b)然后将AtomicReference.executionInputRef指向新的引用;
             *           c)重要：开始验证；
             *
             */
            final Document document = parseResult.getDocument();
            // they may have changed the document and the variables via instrumentation so update the reference to it
            executionInput = executionInput.transform(builder -> builder.variables(parseResult.getVariables()));
            executionInputRef.set(executionInput);

            //fixme 验证：输入、文档、schema：只验证查询在schema上下文的合法性，变量的合法性验证在ValuesResolver.coerceVariableValues中
            final List<ValidationError> errors = validate(executionInput, document, graphQLSchema, instrumentationState);
            if (!errors.isEmpty()) {
                return new PreparsedDocumentEntry(errors);
            }

            //解析、校验都成功了，返回文档
            return new PreparsedDocumentEntry(document);
        }
    }

    /**
     * 解析、并返回结果：解析的异常都是语法的异常、因为解析动作与上下文无关
     */
    private ParseResult parse(ExecutionInput executionInput, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(executionInput, graphQLSchema, instrumentationState);
        //开始解析前，对输入
        InstrumentationContext<Document> parseInstrumentation = instrumentation.beginParse(parameters);

        //创建解析器：调用了antlr、将文本映射到文档对象上？
        DocumentParser documentParser = new DocumentParser();
        Document document;
        DocumentAndVariables documentAndVariables;
        try {
            //解析的文档
            document = documentParser.parseDocument(executionInput.getQuery());
            //包含文档和变量的对象
            documentAndVariables = newDocumentAndVariables()
                    .document(document).variables(executionInput.getVariables()).build();

            documentAndVariables = instrumentation.instrumentDocumentAndVariables(documentAndVariables, parameters);
        } catch (InvalidSyntaxException e) {
            //如果发生语法错误，则返回错误结果
            parseInstrumentation.onCompleted(null, e);
            return ParseResult.ofError(e);
        }
        parseInstrumentation.onCompleted(documentAndVariables.getDocument(), null);
        //返回成功解析的结果
        return ParseResult.of(documentAndVariables);
    }

    /**
     * fixme：重要
     *      此处只验证docuemnt在schema上下文中的合法性；
     *      关于变量信息的验证，比如必要变量是否为空、变量类型等，在ValuesResolver.coerceVariableValues中(变量校验失败dataPresent也是false)
     */
    private List<ValidationError> validate(ExecutionInput executionInput, Document document, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        InstrumentationContext<List<ValidationError>> validationCtx = instrumentation.beginValidation(new InstrumentationValidationParameters(executionInput, document, graphQLSchema, instrumentationState));//验证instrument
        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);

        validationCtx.onCompleted(validationErrors, null);
        return validationErrors;
    }

    /**
     * 输入解析、执行查询、结果解析
     */
    private CompletableFuture<ExecutionResult> execute(ExecutionInput executionInput, Document document, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState) {
        /**
         * 执行策略、拆箱器构造 Execution对象
         */
        Execution execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation, valueUnboxer);
        ExecutionId executionId = executionInput.getExecutionId();

        /**
         * 对象本身：执行策略对象、拆箱——定义了动作；
         * 方法参数：查询文档、schema、输入——定义了输入；
         */
        return execution.execute(document, graphQLSchema, executionId, executionInput, instrumentationState);
    }

    private static Instrumentation checkInstrumentationDefaultState(Instrumentation instrumentation, boolean doNotAddDefaultInstrumentations) {
        if (doNotAddDefaultInstrumentations) {
            return instrumentation == null ? SimpleInstrumentation.INSTANCE : instrumentation;
        }
        if (instrumentation instanceof DataLoaderDispatcherInstrumentation) {
            return instrumentation;
        }
        if (instrumentation == null) {
            return new DataLoaderDispatcherInstrumentation();
        }

        //
        // if we don't have a DataLoaderDispatcherInstrumentation in play, we add one.  We want DataLoader to be 1st class in graphql without requiring
        // people to remember to wire it in.  Later we may decide to have more default instrumentations but for now its just the one
        //
        List<Instrumentation> instrumentationList = new ArrayList<>();
        if (instrumentation instanceof ChainedInstrumentation) {
            instrumentationList.addAll(((ChainedInstrumentation) instrumentation).getInstrumentations());
        } else {
            instrumentationList.add(instrumentation);
        }
        boolean containsDLInstrumentation = instrumentationList.stream().anyMatch(instr -> instr instanceof DataLoaderDispatcherInstrumentation);
        if (!containsDLInstrumentation) {
            instrumentationList.add(new DataLoaderDispatcherInstrumentation());
        }
        return new ChainedInstrumentation(instrumentationList);
    }
}
