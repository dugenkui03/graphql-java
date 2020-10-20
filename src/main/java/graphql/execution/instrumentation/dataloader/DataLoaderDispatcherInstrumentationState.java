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
 * fixme 全局状态类的实现：记录是否可以使用批量查询的状态类。
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

    // "跟踪字段的层级，使得dataLoader更加高效。"
    private final FieldLevelTrackingApproach approach;

    // fixme 此处请求执行使用的dataLoader注册器
    private final AtomicReference<DataLoaderRegistry> dataLoaderRegistry;

    // 该字段同所属对象一样、也是一个 InstrumentationState 实现类
    // fixme 看看这个 组合字段 中保存了什么信息
    private final InstrumentationState state;

    // "积极的 批量"
    private volatile boolean aggressivelyBatching = true;

    // "没有dataloader"
    private volatile boolean hasNoDataLoaders;

    public DataLoaderDispatcherInstrumentationState(Logger log, DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = new AtomicReference<>(dataLoaderRegistry);
        this.approach = new FieldLevelTrackingApproach(log, this::getDataLoaderRegistry);
        this.state = approach.createState();
        hasNoDataLoaders = checkForNoDataLoader(dataLoaderRegistry);
    }

    /**
     * @return  没有使用 ?dataLoader?
     */
    private boolean checkForNoDataLoader(DataLoaderRegistry dataLoaderRegistry) {
        //
        // if they have never set a dataloader into the execution input
        // then we can optimize away the tracking code
        //
        return dataLoaderRegistry == EMPTY_DATALOADER_REGISTRY;
    }

    // "积极的 批量"
    boolean isAggressivelyBatching() {
        return aggressivelyBatching;
    }

    void setAggressivelyBatching(boolean aggressivelyBatching) {
        this.aggressivelyBatching = aggressivelyBatching;
    }

    // "跟踪字段的层级，使得dataLoader更加高效。"
    FieldLevelTrackingApproach getApproach() {
        return approach;
    }

    // 获取dataLoader
    DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistry.get();
    }

    // 设置dataLoader的时候：
    // 1. 更新院子引用；2. 更新dataLoader是否是空dataLoader的标志。
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
