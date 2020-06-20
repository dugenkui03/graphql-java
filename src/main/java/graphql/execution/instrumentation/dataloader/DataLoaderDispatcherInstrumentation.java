package graphql.execution.instrumentation.dataloader;

import graphql.execution.ExecutionResult;
import graphql.execution.ExecutionResultImpl;
import graphql.execution.DataFetcher;
import graphql.execution.strategy.AsyncExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.strategy.ExecutionStrategy;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.node.definition.OperationDefinition;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * fixme
 *      当每一个级别的查询执行时、该Instrumentation将会派遣所有的DataLoader
 *
 * This graphql {@link graphql.execution.instrumentation.Instrumentation} will dispatch
 * all the contained {@link org.dataloader.DataLoader}s when each level of the graphql
 * query is executed.
 * <p>
 *
 * fixme 允许你在dataLoader中使用dataload来优化数据的加载
 *
 * This allows you to use {@link org.dataloader.DataLoader}s in your {@link DataFetcher}s
 * to optimal loading of data.
 * <p>
 *     fixme 该instrument是GrapQL的默认instrument；
 * A DataLoaderDispatcherInstrumentation will be automatically added to the {@link graphql.GraphQL}
 * instrumentation list if one is not present.
 *
 * @see org.dataloader.DataLoader
 * @see org.dataloader.DataLoaderRegistry
 */
public class DataLoaderDispatcherInstrumentation extends SimpleInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderDispatcherInstrumentation.class);

    private final DataLoaderDispatcherInstrumentationOptions options;

    /**
     *  //默认不包含统计信息
     * Creates a DataLoaderDispatcherInstrumentation with the default options
     */
    public DataLoaderDispatcherInstrumentation() {
        this(DataLoaderDispatcherInstrumentationOptions.newOptions());
    }

    //使用指定的选项创建Instrumentation
    public DataLoaderDispatcherInstrumentation(DataLoaderDispatcherInstrumentationOptions options) {
        this.options = options;
    }


    //fixme InstrumentationCreateStateParameters：包含schema和输入信息
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new DataLoaderDispatcherInstrumentationState(log, parameters.getExecutionInput().getDataLoaderRegistry());
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //默认值为true，所以在这里返回
        if (state.isAggressivelyBatching()) {
            return dataFetcher;
        }
        //
        // currently only AsyncExecutionStrategy with DataLoader and hence this allows us to "dispatch"
        // on every object if its not using aggressive batching for other execution strategies
        // which allows them to work if used.
        return (DataFetcher<Object>) environment -> {
            Object obj = dataFetcher.get(environment);
            immediatelyDispatch(state);
            return obj;
        };
    }

    /**
     * 立即派遣
     */
    private void immediatelyDispatch(DataLoaderDispatcherInstrumentationState state) {
        state.getApproach().dispatch();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // during #instrumentExecutionInput they could have enhanced the data loader registry
        // so we grab it now just before the query operation gets started
        //
        DataLoaderRegistry finalRegistry = parameters.getExecutionContext().getDataLoaderRegistry();
        state.setDataLoaderRegistry(finalRegistry);
        if (!isDataLoaderCompatibleExecution(parameters.getExecutionContext())) {
            state.setAggressivelyBatching(false);
        }
        return new SimpleInstrumentationContext<>();
    }

    private boolean isDataLoaderCompatibleExecution(ExecutionContext executionContext) {
        //
        // currently we only support Query operations and ONLY with AsyncExecutionStrategy as the query ES
        // This may change in the future but this is the fix for now
        //
        if (executionContext.getOperationDefinition().getOperation() == OperationDefinition.Operation.QUERY) {
            ExecutionStrategy queryStrategy = executionContext.getQueryStrategy();
            if (queryStrategy instanceof AsyncExecutionStrategy) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return new ExecutionStrategyInstrumentationContext() {
                @Override
                public void onDispatched(CompletableFuture<ExecutionResult> result) {
                }

                @Override
                public void onCompleted(ExecutionResult result, Throwable t) {
                }
            };

        }
        return state.getApproach().beginExecutionStrategy(parameters.withNewState(state.getState()));
    }

    @Override
    public DeferredFieldInstrumentationContext beginDeferredField(InstrumentationDeferredFieldParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //
        // if there are no data loaders, there is nothing to do
        //
        if (state.hasNoDataLoaders()) {
            return new DeferredFieldInstrumentationContext() {
                @Override
                public void onDispatched(CompletableFuture<ExecutionResult> result) {
                }

                @Override
                public void onCompleted(ExecutionResult result, Throwable t) {
                }
            };

        }
        return state.getApproach().beginDeferredField(parameters.withNewState(state.getState()));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        //如果没有dataloader、则直接返回、什么都不用做
        if (state.hasNoDataLoaders()) {
            return new SimpleInstrumentationContext<>();
        }
        //如果使用了dataloader
        return state.getApproach().beginFieldFetch(parameters.withNewState(state.getState()));
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        if (!options.isIncludeStatistics()) {
            return CompletableFuture.completedFuture(executionResult);
        }
        DataLoaderDispatcherInstrumentationState state = parameters.getInstrumentationState();
        Map<Object, Object> currentExt = executionResult.getExtensions();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);
        Map<Object, Object> dataLoaderStats = buildStatsMap(state);
        statsMap.put("dataloader", dataLoaderStats);

        log.debug("Data loader stats : {}", dataLoaderStats);

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), statsMap));
    }

    private Map<Object, Object> buildStatsMap(DataLoaderDispatcherInstrumentationState state) {
        DataLoaderRegistry dataLoaderRegistry = state.getDataLoaderRegistry();
        Statistics allStats = dataLoaderRegistry.getStatistics();
        Map<Object, Object> statsMap = new LinkedHashMap<>();
        statsMap.put("overall-statistics", allStats.toMap());

        Map<Object, Object> individualStatsMap = new LinkedHashMap<>();

        for (String dlKey : dataLoaderRegistry.getKeys()) {
            DataLoader<Object, Object> dl = dataLoaderRegistry.getDataLoader(dlKey);
            Statistics statistics = dl.getStatistics();
            individualStatsMap.put(dlKey, statistics.toMap());
        }

        statsMap.put("individual-statistics", individualStatsMap);

        return statsMap;
    }
}