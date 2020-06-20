package graphql.execution.utils;

import graphql.util.Assert;
import graphql.masker.Internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Internal
@SuppressWarnings("FutureReturnValueIgnored")
public class AsyncUtil {

    @FunctionalInterface
    public interface CFFactory<T, U> {
        CompletableFuture<U> apply(T input, int index, List<U> previousResults);
    }

    /**
     * @return 等futures中所有任务结束后，
     *         将结果包装在 CompletableFuture<List<U>> 中返回
     */
    public static <U> CompletableFuture<List<U>> each(List<CompletableFuture<U>> futures) {
        CompletableFuture<List<U>> overallResult = new CompletableFuture<>();
        /**
         * https://colobu.com/2016/02/29/Java-CompletableFuture/
         *
         * fixme:
         *      1. toArray(new SomeType[0]) 将返回确定类型的数组，而非 Object[]
         *         CompletableFuture[] completableFutures = futures.toArray(new CompletableFuture[0]);
         *      2. allOf方法是当所有的CompletableFuture都执行完后执行计算;
         *      3. whenComplete：结束计算时、执行该方法中的函数方法 BiConsumer void accept(T t, U u)；
         *      4. completeExceptionally和complete设定CompletableFuture的结果，可对应到之前调用get()的结果。
         *
         */
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((noUsed, exception) -> {
                    /**
                     * 如果时异常结果，则将异常包装?
                     * 似乎是：如果有异常，不可能走到这个里边来。如下代码不会有任何输出
                     *
                     * <pre>
                     *     {@code
                     *         CompletableFuture<Integer> future = new CompletableFuture<>();
                     *         future.complete(1);
                     *
                     *         CompletableFuture<Integer> future2 = new CompletableFuture<>();
                     *         future2.complete(2);
                     *
                     *         CompletableFuture<Integer> future3 = new CompletableFuture<>();
                     *         future2.exceptionally(e -> 3);
                     *         CompletableFuture.allOf(future, future2, future3).whenComplete(
                     *                 (r, e) -> {
                     *                     System.out.println(r + "." + e);
                     *                 }
                     *         );
                     *      }
                     *  </pre>
                     */
                    if (exception != null) {
                        overallResult.completeExceptionally(exception);
                        return;
                    }
                    //因为已经 allOf了，所以此时futures肯定已经全部结束了
                    List<U> results = new ArrayList<>();
                    for (CompletableFuture<U> future : futures) {
                        results.add(future.join());
                    }
                    overallResult.complete(results);
                });
        return overallResult;
    }

    public static <T, U> CompletableFuture<List<U>> each(Iterable<T> list, BiFunction<T, Integer, CompletableFuture<U>> cfFactory) {
        List<CompletableFuture<U>> futures = new ArrayList<>();
        int index = 0;
        for (T t : list) {
            CompletableFuture<U> cf;
            try {
                cf = cfFactory.apply(t, index++);
                Assert.assertNotNull(cf, "cfFactory must return a non null value");
            } catch (Exception e) {
                cf = new CompletableFuture<>();
                // Async.each makes sure that it is not a CompletionException inside a CompletionException
                cf.completeExceptionally(new CompletionException(e));
            }
            futures.add(cf);
        }
        return each(futures);

    }

    public static <T, U> CompletableFuture<List<U>> eachSequentially(Iterable<T> list, CFFactory<T, U> cfFactory) {
        CompletableFuture<List<U>> result = new CompletableFuture<>();
        eachSequentiallyImpl(list.iterator(), cfFactory, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T, U> void eachSequentiallyImpl(Iterator<T> iterator, CFFactory<T, U> cfFactory, int index, List<U> tmpResult, CompletableFuture<List<U>> overallResult) {
        if (!iterator.hasNext()) {
            overallResult.complete(tmpResult);
            return;
        }
        CompletableFuture<U> cf;
        try {
            cf = cfFactory.apply(iterator.next(), index, tmpResult);
            Assert.assertNotNull(cf, "cfFactory must return a non null value");
        } catch (Exception e) {
            cf = new CompletableFuture<>();
            cf.completeExceptionally(new CompletionException(e));
        }
        cf.whenComplete((cfResult, exception) -> {
            if (exception != null) {
                overallResult.completeExceptionally(exception);
                return;
            }
            tmpResult.add(cfResult);
            eachSequentiallyImpl(iterator, cfFactory, index + 1, tmpResult, overallResult);
        });
    }


    /**
     * Turns an object T into a CompletableFuture if its not already
     *
     * @param t   - the object to check
     * @param <T> for two
     *
     * @return a CompletableFuture
     */
    public static <T> CompletableFuture<T> toCompletableFuture(T t) {
        if (t instanceof CompletionStage) {
            //noinspection unchecked
            return ((CompletionStage<T>) t).toCompletableFuture();
        } else {
            return CompletableFuture.completedFuture(t);
        }
    }

    public static <T> CompletableFuture<T> tryCatch(Supplier<CompletableFuture<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            CompletableFuture<T> result = new CompletableFuture<>();
            //如果result包装的任务还没结束，调用get就会抛出completeExceptionally包装的异常
            result.completeExceptionally(e);
            return result;
        }
    }

    /**
     * CompletableFuture任务以异常结束
     * @return
     */
    public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable exception) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(exception);
        return result;
    }

    public static <T> void copyResults(CompletableFuture<T> source, CompletableFuture<T> target) {
        source.whenComplete((o, throwable) -> {
            if (throwable != null) {
                target.completeExceptionally(throwable);
                return;
            }
            target.complete(o);
        });
    }


    public static <U, T> CompletableFuture<U> reduce(List<CompletableFuture<T>> values, U initialValue, BiFunction<U, T, U> aggregator) {
        CompletableFuture<U> result = new CompletableFuture<>();
        reduceImpl(values, 0, initialValue, aggregator, result);
        return result;
    }

    public static <U, T> CompletableFuture<U> reduce(CompletableFuture<List<T>> values, U initialValue, BiFunction<U, T, U> aggregator) {
        return values.thenApply(list -> {
            U result = initialValue;
            for (T value : list) {
                result = aggregator.apply(result, value);
            }
            return result;
        });
    }

    public static <U, T> CompletableFuture<List<U>> flatMap(List<T> inputs, Function<T, CompletableFuture<U>> mapper) {
        List<CompletableFuture<U>> collect = inputs
                .stream()
                .map(mapper)
                .collect(Collectors.toList());
        return AsyncUtil.each(collect);
    }

    private static <U, T> void reduceImpl(List<CompletableFuture<T>> values, int curIndex, U curValue, BiFunction<U, T, U> aggregator, CompletableFuture<U> result) {
        if (curIndex == values.size()) {
            result.complete(curValue);
            return;
        }
        values.get(curIndex).
                thenApply(oneValue -> aggregator.apply(curValue, oneValue))
                .thenAccept(newValue -> reduceImpl(values, curIndex + 1, newValue, aggregator, result));
    }

    public static <U, T> CompletableFuture<List<U>> map(CompletableFuture<List<T>> values, Function<T, U> mapper) {
        return values.thenApply(list -> list.stream().map(mapper).collect(Collectors.toList()));
    }

    public static <U, T> List<CompletableFuture<U>> map(List<CompletableFuture<T>> values, Function<T, U> mapper) {
        return values
                .stream()
                .map(cf -> cf.thenApply(mapper::apply)).collect(Collectors.toList());
    }

    public static <U, T> List<CompletableFuture<U>> mapCompose(List<CompletableFuture<T>> values, Function<T, CompletableFuture<U>> mapper) {
        return values
                .stream()
                .map(cf -> cf.thenCompose(mapper::apply)).collect(Collectors.toList());
    }

}
