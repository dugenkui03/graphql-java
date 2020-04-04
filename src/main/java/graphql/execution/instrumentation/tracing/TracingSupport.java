package graphql.execution.instrumentation.tracing;

import graphql.PublicApi;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.DataFetchingEnvironment;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static graphql.schema.GraphQLTypeUtil.simplePrint;

/**
 * This creates a map of tracing information as outlined in https://github.com/apollographql/apollo-tracing
 * <p>
 * This is a stateful object that should be instantiated and called via {@link java.lang.instrument.Instrumentation}
 * calls.  It has been made a separate class so that you can compose this into existing
 * instrumentation code.
 */
@PublicApi
public class TracingSupport implements InstrumentationState {

    /**
     * 请求开始时间，executeAsync处间接调用
     * 所以是每个请求就会生成一个instrument
     */
    private final Instant startRequestTime;

    /**
     * 开始请求时间、纳秒单位
     */
    private final long startRequestNanos;

    /**
     * 元素是某个字段的执行信息。
     */
    private final ConcurrentLinkedQueue<Map<String, Object>> fieldData;

    /**
     * 解析和验证时间map时间map
     */
    private final Map<String, Object> parseMap = new LinkedHashMap<>(2);
    private final Map<String, Object> validationMap = new LinkedHashMap<>(2);
    //是否跟踪trivial标记的dataFetcher
    private final boolean includeTrivialDataFetchers;

    /**
     * 创建InstrumentationState的时间、就是请求开始的时间。
     *
     * The timer starts as soon as you create this object
     *
     * @param includeTrivialDataFetchers whether the trace trivial data fetchers
     */
    public TracingSupport(boolean includeTrivialDataFetchers) {
        this.includeTrivialDataFetchers = includeTrivialDataFetchers;
        startRequestNanos = System.nanoTime();
        startRequestTime = Instant.now();
        fieldData = new ConcurrentLinkedQueue<>();
    }

    /**
     * 一个简单的对象、可以用来调用 onEnd() 方法
     * A simple object that you need to call {@link #onEnd()} on
     */
    public interface TracingContext {
        /**
         * 调用他来结束当前跟踪的上下文
         * Call this to end the current trace context
         */
        void onEnd();
    }

    /**
     * fixme 在instrumentDataFetcher和dataFetcher.get()之前调用
     *
     * This should be called to start the trace of a field, with {@link TracingContext#onEnd()} being called to
     * end the call.
     *
     * @param dataFetchingEnvironment the data fetching that is occurring
     * @param trivialDataFetcher      if the data fetcher is considered trivial
     *
     * @return a context to call end on
     */
    public TracingContext beginField(DataFetchingEnvironment dataFetchingEnvironment, boolean trivialDataFetcher) {

        /**
         * 如果确定跟踪trivial 定义的且是当前dataFetcher就是trivial类型的dataFetcher，则返回空
         */
        if (!includeTrivialDataFetchers && trivialDataFetcher) {
            return () -> {
                // nothing to do
            };
        }

        /**
         * 开始获取字段时间
         */
        long startFieldFetch = System.nanoTime();


        return () -> {
            /**
             * 获取字段动作 执行时间
             */
            long now = System.nanoTime();
            long duration = now - startFieldFetch;

            /**
             * 获取字段动作 开始执行时、距离请求的时间。
             */
            long startOffset = startFieldFetch - startRequestNanos;

            Map<String, Object> fetchMap = new LinkedHashMap<>();

            ExecutionStepInfo executionStepInfo = dataFetchingEnvironment.getExecutionStepInfo();
            /**
             * fixme 处理的字段路径
             */
            fetchMap.put("path", executionStepInfo.getPath().toList());
            /**
             * todo 所在类型
             */
            fetchMap.put("parentType", simplePrint(executionStepInfo.getParent().getUnwrappedNonNullType()));

            /**
             * 字段类型
             */
            fetchMap.put("returnType", executionStepInfo.simplePrint());

            /**
             * 字段名称
             */
            fetchMap.put("fieldName", executionStepInfo.getFieldDefinition().getName());

            fetchMap.put("startOffset", startOffset);
            fetchMap.put("duration", duration);

            fieldData.add(fetchMap);
        };
    }

    /**
     * 调用此方法来追踪parse过程，并在结束的时候调用TracingContext.onEnd()
     * This should be called to start the trace of query parsing,
     * with {@link TracingContext#onEnd()} being called to end the call.
     *
     * @return a context to call end on
     */
    public TracingContext beginParse() {
        return traceToMap(parseMap);
    }

    /**
     * 在验证开始的时候调用，并在结束的时候调用TracingContext.onEnd()
     * This should be called to start the trace of query validation, with {@link TracingContext#onEnd()} being called to
     * end the call.
     *
     * @return a context to call end on
     */
    public TracingContext beginValidation() {
        return traceToMap(validationMap);
    }


    private TracingContext traceToMap(Map<String, Object> map) {
        //开始时间
        long start = System.nanoTime();

        return () -> {
            //持续时间和距离请求的时间
            long now = System.nanoTime();
            long duration = now - start;
            long startOffset = now - startRequestNanos;

            map.put("startOffset", startOffset);
            map.put("duration", duration);
        };
    }

    /**
     * 将会获取追踪信息的快照、并以map类型返回
     * This will snapshot this tracing and return a map of the results
     *
     * @return a snapshot of the tracing data
     */
    public Map<String, Object> snapshotTracingData() {

        Map<String, Object> traceMap = new LinkedHashMap<>();
        //版本一直是1，xixi
        traceMap.put("version", 1L);
        //
        traceMap.put("startTime", rfc3339(startRequestTime));
        traceMap.put("endTime", rfc3339(Instant.now()));
        traceMap.put("duration", System.nanoTime() - startRequestNanos);
        traceMap.put("parsing", copyMap(parseMap));
        traceMap.put("validation", copyMap(validationMap));
        traceMap.put("execution", executionData());

        return traceMap;
    }


    //复制并返回map
    private Object copyMap(Map<String, Object> map) {
        Map<String, Object> mapCopy = new LinkedHashMap<>();
        mapCopy.putAll(map);
        return mapCopy;
    }

    private Map<String, Object> executionData() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>(fieldData);
        map.put("resolvers", list);
        return map;
    }

    private String rfc3339(Instant time) {
        return DateTimeFormatter.ISO_INSTANT.format(time);
    }

}
