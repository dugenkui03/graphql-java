package graphql.execution.instrumentation.tracing;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import graphql.execution.instrumentation.SimpleInstrumentationContext;

/**
 * 这个fetcher使用TracingSupport来捕获 trace追踪信息，并放到结果中
 * This {@link Instrumentation} implementation uses {@link TracingSupport} to
 * capture tracing information and puts it into the {@link ExecutionResult}
 */
@PublicApi
public class TracingInstrumentation extends SimpleInstrumentation {

    public static class Options {
        //是否包含不重要的DataFetcher
        private final boolean includeTrivialDataFetchers;

        private Options(boolean includeTrivialDataFetchers) {
            this.includeTrivialDataFetchers = includeTrivialDataFetchers;
        }

        public boolean isIncludeTrivialDataFetchers() {
            return includeTrivialDataFetchers;
        }

        /**
         * PropertyDataFetcher默认包含在跟踪器中
         * <p></p>
         * By default trivial data fetchers (those that simple pull data from an object into field) are included
         * in tracing but you can control this behavior.
         *
         * @param flag the flag on whether to trace trivial data fetchers
         *
         * @return a new options object
         */
        public Options includeTrivialDataFetchers(boolean flag) {
            return new Options(flag);
        }

        public static Options newOptions() {
            return new Options(true);
        }

    }

    public TracingInstrumentation() {
        this(Options.newOptions());
    }

    public TracingInstrumentation(Options options) {
        this.options = options;
    }

    private final Options options;

    @Override
    public InstrumentationState createState() {
        return new TracingSupport(options.includeTrivialDataFetchers);
    }

    /**
     * 将追踪信息放到最后的结果中
     *
     * ExecutionResult——最终返回结果，允许instrument结果
     */
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        /**
         * 获取 拓展结果
         */
        Map<Object, Object> currentExt = executionResult.getExtensions();

        /**
         * 获取InstrumentationState的实现类：跟踪辅助类：TracingSupport
         */
        TracingSupport tracingSupport = parameters.getInstrumentationState();

        /**
         * 初始化 "跟踪map"
         */
        Map<Object, Object> tracingMap = new LinkedHashMap<>();

        /**
         * 在"跟踪map"中放入 拓展结果
         *
         */
        tracingMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);

        /**
         * fixme：将字段跟踪信息放到 拓展结果中
         */
        tracingMap.put("tracing", tracingSupport.snapshotTracingData());

        /**
         * 返回最终结果+拓展结果
         */
        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), tracingMap));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginField(parameters.getEnvironment(), parameters.isTrivialDataFetcher());
        return SimpleInstrumentationContext.whenCompleted((result, t) -> ctx.onEnd());
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginParse();
        return SimpleInstrumentationContext.whenCompleted((result, t) -> ctx.onEnd());
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginValidation();
        return SimpleInstrumentationContext.whenCompleted((result, t) -> ctx.onEnd());
    }
}
