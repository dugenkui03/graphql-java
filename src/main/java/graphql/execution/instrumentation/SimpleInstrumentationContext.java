package graphql.execution.instrumentation;

import graphql.PublicApi;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A simple implementation of {@link InstrumentationContext}
 *
 * 实现了onDispatched和onCompleted，默认啥也不做，但是可以在构造参数重指定其行为
 */
@PublicApi
public class SimpleInstrumentationContext<T> implements InstrumentationContext<T> {


    private final BiConsumer<T, Throwable> codeToRunOnComplete;
    private final Consumer<CompletableFuture<T>> codeToRunOnDispatch;

    /**
     * A context that does nothing
     * 静态工具类，生成啥也不做的SimpleInstrumentationContext
     *
     * @param <T> the type needed
     *
     * @return a context that does nothing
     */
    public static <T> InstrumentationContext<T> noOp() {
        return new SimpleInstrumentationContext<>();
    }

    public SimpleInstrumentationContext() {
        this(null, null);
    }

    /**
     * @param codeToRunOnDispatch 请求派发的时候
     *
     * @param codeToRunOnComplete 请求完成的时候
     */
    private SimpleInstrumentationContext(Consumer<CompletableFuture<T>> codeToRunOnDispatch,
                                         BiConsumer<T, Throwable> codeToRunOnComplete) {
        this.codeToRunOnComplete = codeToRunOnComplete;
        this.codeToRunOnDispatch = codeToRunOnDispatch;
    }

    @Override
    public void onDispatched(CompletableFuture<T> result) {
        if (codeToRunOnDispatch != null) {
            codeToRunOnDispatch.accept(result);
        }
    }

    @Override
    public void onCompleted(T result, Throwable t) {
        if (codeToRunOnComplete != null) {
            codeToRunOnComplete.accept(result, t);
        }
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step dispatch.
     *
     * @param codeToRun the code to run on dispatch
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenDispatched(Consumer<CompletableFuture<U>> codeToRun) {
        return new SimpleInstrumentationContext<>(codeToRun, null);
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step completion.
     *
     * @param codeToRun the code to run on completion
     * @param <U>       the generic type
     *
     * @return an instrumentation context
     */
    public static <U> SimpleInstrumentationContext<U> whenCompleted(BiConsumer<U, Throwable> codeToRun) {
        return new SimpleInstrumentationContext<>(null, codeToRun);
    }
}
