package graphql.execution.instrumentation.tracing;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetchingEnvironment;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static graphql.schema.GraphQLTypeUtil.simplePrint;

/**
 * This creates a map of tracing information as outlined in https://github.com/apollographql/apollo-tracing
 * <p>
 * This is a stateful object that should be instantiated and called
 * via {@link java.lang.instrument.Instrumentation} calls.  It has been made
 * a separate class so that you can compose this into existing instrumentation code.
 */
@PublicApi
public class TracingSupport implements InstrumentationState {

    // 请求开始时间，在创建实例的时候初始化
    private final Instant startRequestTime;
    private final long startRequestNanos;

    // 队列，保存 todo
    private final ConcurrentLinkedQueue<Map<String, Object>> fieldData;


    private final Map<String, Object> parseMap = new LinkedHashMap<>();
    private final Map<String, Object> validationMap = new LinkedHashMap<>();
    private final boolean includeTrivialDataFetchers;

    /**
     * The timer starts as soon as you create this object
     *
     * @param includeTrivialDataFetchers whether the trace trivial data fetchers
     */
    public TracingSupport(boolean includeTrivialDataFetchers) {
        // 是否跟踪TrivialDataFetcher的解析性能
        this.includeTrivialDataFetchers = includeTrivialDataFetchers;

        // 请求开始时间，在创建实例的时候初始化
        startRequestNanos = System.nanoTime();
        startRequestTime = Instant.now();

        fieldData = new ConcurrentLinkedQueue<>();
    }

    /**
     * fixme 定义一个回调动作，在onEnd()中执行
     *
     * A simple object that you need to call {@link #onEnd()} on
     */
    public interface TracingContext {
        /**
         * Call this to end the current trace context
         */
        void onEnd();
    }

    /**
     * This should be called to start the trace of a field,
     * with {@link TracingContext#onEnd()} being called to end the call.
     *
     * fixme
     *      开始跟踪一个字段的时候，调用beginField();
     *      结束跟踪一个字段的时候，执行TracingContext.onEnd()方法，更新记录状态。
     *
     * @param dataFetchingEnvironment the data fetching that is occurring
     * @param trivialDataFetcher      if the data fetcher is considered trivial
     *
     * @return a context to call end on
     */
    public TracingContext beginField(DataFetchingEnvironment dataFetchingEnvironment, boolean trivialDataFetcher) {
        // 如果是 trivialDataFetcher，则啥也不做。
        if (!includeTrivialDataFetchers && trivialDataFetcher) {
            return () -> { };
        }

        // 开始解析字段的时间点
        long startFieldFetch = System.nanoTime();
        return () -> {
            // 解析解析字段的时间点
            long now = System.nanoTime();
            // 解析时间持续时长
            long duration = now - startFieldFetch;
            // 开始解析当前字段的延迟，todo 可以放在外边
            long startOffset = startFieldFetch - startRequestNanos;

            //
            ExecutionStepInfo executionStepInfo = dataFetchingEnvironment.getExecutionStepInfo();
            Map<String, Object> fetchMap = new LinkedHashMap<>();
            fetchMap.put("path", executionStepInfo.getPath().toList());

            fetchMap.put("parentType", simplePrint(executionStepInfo.getParent().getUnwrappedNonNullType()));
            fetchMap.put("returnType", executionStepInfo.simplePrint());
            fetchMap.put("fieldName", executionStepInfo.getFieldDefinition().getName());
            fetchMap.put("startOffset", startOffset);
            fetchMap.put("duration", duration);

            fieldData.add(fetchMap);
        };
    }

    /**
     * This should be called to start the trace of query parsing, with {@link TracingContext#onEnd()} being called to
     * end the call.
     *
     * @return a context to call end on
     */
    public TracingContext beginParse() {
        return traceToMap(parseMap);
    }

    /**
     * This should be called to start the trace of query validation, with {@link TracingContext#onEnd()} being called to
     * end the call.
     *
     * @return a context to call end on
     */
    public TracingContext beginValidation() {
        return traceToMap(validationMap);
    }

    private TracingContext traceToMap(Map<String, Object> map) {
        long start = System.nanoTime();
        return () -> {
            long now = System.nanoTime();
            long duration = now - start;
            long startOffset = now - startRequestNanos;

            map.put("startOffset", startOffset);
            map.put("duration", duration);
        };
    }

    /**
     * This will snapshot this tracing and return a map of the results
     *
     * @return a snapshot of the tracing data
     */
    public Map<String, Object> snapshotTracingData() {

        Map<String, Object> traceMap = new LinkedHashMap<>();
        traceMap.put("version", 1L);
        traceMap.put("startTime", rfc3339(startRequestTime));
        traceMap.put("endTime", rfc3339(Instant.now()));
        traceMap.put("duration", System.nanoTime() - startRequestNanos);
        traceMap.put("parsing", copyMap(parseMap));
        traceMap.put("validation", copyMap(validationMap));
        traceMap.put("execution", executionData());

        return traceMap;
    }

    private Object copyMap(Map<String, Object> map) {
        return new LinkedHashMap<>(map);
    }

    private Map<String, Object> executionData() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> list = ImmutableList.copyOf(fieldData);
        map.put("resolvers", list);
        return map;
    }

    private String rfc3339(Instant time) {
        return DateTimeFormatter.ISO_INSTANT.format(time);
    }

}
