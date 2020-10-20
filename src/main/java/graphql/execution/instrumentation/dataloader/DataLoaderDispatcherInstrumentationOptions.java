package graphql.execution.instrumentation.dataloader;

import graphql.PublicApi;

/**
 * The options that control the operation of
 * {@link graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation}
 *
 * fixme
 *      控制 DataLoaderDispatcherInstrumentation 操作的配置：
 *      是否将对 java-dataloader 的统计数据放到结果中
 */
@PublicApi
public class DataLoaderDispatcherInstrumentationOptions {

    // 是否将对 java-dataloader 的统计数据放到结果中
    private final boolean includeStatistics;

    private DataLoaderDispatcherInstrumentationOptions(boolean includeStatistics) {
        this.includeStatistics = includeStatistics;
    }

    public static DataLoaderDispatcherInstrumentationOptions newOptions() {
        return new DataLoaderDispatcherInstrumentationOptions(false);
    }

    /**
     * This will toggle(切换) the ability to include java-dataloader statistics into the extensions output of your query
     * fixme 是否输出统计结果。
     *
     * @param flag the switch to follow
     *
     * @return a new options object
     */
    public DataLoaderDispatcherInstrumentationOptions includeStatistics(boolean flag) {
        return new DataLoaderDispatcherInstrumentationOptions(flag);
    }

    // 是否将对 java-dataloader 的统计数据放到结果中
    public boolean isIncludeStatistics() {
        return includeStatistics;
    }

}
