package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 跟踪是否可以使用主动批处理的基本类
 * A base class that keeps track of whether aggressive batching can be used
 */
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    //通过"字段层级跟踪"使得数据加载器更加高效
    private final FieldLevelTrackingApproach approach;

    //dataloader注册器的原子引用
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;

    // 跟踪是否可以使用主动批处理的基本类
    private final InstrumentationState state;

    //是否是主动的批处理、没有dataloader？
    private volatile boolean aggressivelyBatching = true;
    private volatile boolean hasNoDataLoaders;

    @Internal
    public static final DataLoaderRegistry EMPTY_DATALOADER_REGISTRY = new DataLoaderRegistry() {
        @Override
        public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
            return Assert.assertShouldNeverHappen("You MUST set in your own DataLoaderRegistry to use data loader");
        }
    };


    /**
     * @param log 日志类
     * @param dataLoaderRegistry dataLoader注册器，放在原子引用中
     */
    public DataLoaderDispatcherInstrumentationState(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);

        this.approach = new FieldLevelTrackingApproach(log, this::getDataLoaderRegistry);
        this.state = approach.createState();

        hasNoDataLoaders = checkForNoDataLoader(dataLoaderRegistry);
    }

    private boolean checkForNoDataLoader(DataLoaderRegistry dataLoaderRegistry) {
        // 如果有没设置过任何dataloader，按我们可以 优化掉 optimize away 跟踪代码
        // if they have never set a dataloader into the execution input then we can optimize away the tracking code
        return dataLoaderRegistry == EMPTY_DATALOADER_REGISTRY;
    }

    boolean isAggressivelyBatching() {
        return aggressivelyBatching;
    }

    void setAggressivelyBatching(boolean aggressivelyBatching) {
        this.aggressivelyBatching = aggressivelyBatching;
    }

    FieldLevelTrackingApproach getApproach() {
        return approach;
    }

    DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry.get();
    }

    void setDataLoaderRegistry(DataLoaderRegistry newRegistry) {
        dataLoaderRegistry.set(newRegistry);
        hasNoDataLoaders = checkForNoDataLoader(newRegistry);
    }

    boolean hasNoDataLoaders() {
        return hasNoDataLoaders;
    }

    InstrumentationState getState() {
        return state;
    }
}
