package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.PublicApi;
import graphql.execution.instrumentation.InstrumentationState;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A base class that keeps track of whether aggressive(adj: 有侵略性的，好斗的) batching can be used
 *
 * fixme 记录是否可以使用批量查询的状态类。
 */
@PublicApi
public class DataLoaderDispatcherInstrumentationState implements InstrumentationState {

    // 空-dataLoader注册器
    // final 关键字修饰的，跟只有get、没有set的属性一样
    @Internal
    public static final DataLoaderRegistry EMPTY_DATALOADER_REGISTRY = new DataLoaderRegistry() {
        @Override
        public DataLoaderRegistry register(String key, DataLoader<?, ?> dataLoader) {
            // 必须设置自己的 DataLoaderRegistry、来使用DataLoader
            return Assert.assertShouldNeverHappen("You MUST set in your own DataLoaderRegistry to use data loader");
        }
    };

    //
    private final FieldLevelTrackingApproach approach;
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;
    private final InstrumentationState state;
    private volatile boolean aggressivelyBatching = true;
    private volatile boolean hasNoDataLoaders;

    public DataLoaderDispatcherInstrumentationState(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);
        this.approach = new FieldLevelTrackingApproach(log, this::getDataLoaderRegistry);
        this.state = approach.createState();
        hasNoDataLoaders = checkForNoDataLoader(dataLoaderRegistry);
    }

    private boolean checkForNoDataLoader(DataLoaderRegistry dataLoaderRegistry) {
        //
        // if they have never set a dataloader into the execution input then we can optimize
        // away the tracking code
        //
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
