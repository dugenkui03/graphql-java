package graphql.execution;

import graphql.Assert;
import graphql.Internal;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Internal
@SuppressWarnings("FutureReturnValueIgnored")
public class Async {

    @FunctionalInterface
    public interface CFFactory<T, U> {
        CompletableFuture<U> apply(T input, int index, List<U> previousResults);
    }

    public static <U> CompletableFuture<List<U>> each(List<CompletableFuture<U>> futures) {

        CompletableFuture<List<U>> overallResult = new CompletableFuture<>();

        // list to array
        @SuppressWarnings("unchecked")
        CompletableFuture<U>[] arrayOfFutures = futures.toArray(new CompletableFuture[0]);

        /**
         * fixme step_1：等所有的任务完成
         *       当所有的CompletableFuture都执行完后执行计算
         */
        CompletableFuture.allOf(arrayOfFutures)

                //fixme 重点：总体结果无法反映在返回结果中，单独的 get()/join() 元素任务分析结果。
                // whenComplete
                .whenComplete((ignored, exception) -> {

                    // 指定完成异常
                    if (exception != null) {
                        overallResult.completeExceptionally(exception);
                        return;
                    }

                    /**
                     * fixme step_2：将结果放到list中
                     *       获取每个任务的结果放到总体结果中
                     */
                    List<U> results = new ArrayList<>(arrayOfFutures.length);
                    for (CompletableFuture<U> future : arrayOfFutures) {
                        results.add(future.join());
                    }
                    overallResult.complete(results);
                });
        return overallResult;
    }

    public static <T, U> CompletableFuture<List<U>> each(Collection<T> list, BiFunction<T, Integer, CompletableFuture<U>> cfFactory) {
        List<CompletableFuture<U>> futures = new ArrayList<>(list.size());
        int index = 0;
        for (T t : list) {
            CompletableFuture<U> cf;
            try {
                cf = cfFactory.apply(t, index++);
                Assert.assertNotNull(cf, () -> "cfFactory must return a non null value");
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
            Assert.assertNotNull(cf, () -> "cfFactory must return a non null value");
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
            //noinspection unchecked 返回本身
            return ((CompletionStage<T>) t).toCompletableFuture();
        } else {
            // 使用 t 作为结果、返回一个已经完成计算的CompletableFuture。
            return CompletableFuture.completedFuture(t);
        }
    }

    //在try、catch中执行Supplier.get()的逻辑
    public static <T> CompletableFuture<T> tryCatch(Supplier<CompletableFuture<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            CompletableFuture<T> result = new CompletableFuture<>();
            result.completeExceptionally(e);
            return result;
        }
    }

    // 使用制定异常结果构造 CompletableFuture
    public static <T> CompletableFuture<T> exceptionallyCompletedFuture(Throwable exception) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(exception);
        return result;
    }

    // 将 source 的计算结果拷贝到 target
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

    // Function<参数, 结果>
    public static <U, T> CompletableFuture<List<U>> flatMap(List<T> inputs, Function<T, CompletableFuture<U>> mapper) {
        List<CompletableFuture<U>> collect = inputs
                .stream()
                .map(mapper)
                .collect(Collectors.toList());
        return Async.each(collect);
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

    public static <U, T> CompletableFuture<List<U>> map(CompletableFuture<List<T>> values,
                                                        Function<T, U> mapper) {
        return values.thenApply(
                list -> list.stream().
                        map(mapper).
                        collect(Collectors.toList())
        );
    }

    public static <U, T> List<CompletableFuture<U>> map(List<CompletableFuture<T>> values,
                                                        Function<T, U> mapper) {
        return values.stream()
                .map(cf -> cf.thenApply(mapper::apply))
                .collect(Collectors.toList());
    }

    public static <U, T> List<CompletableFuture<U>> mapCompose(List<CompletableFuture<T>> values,
                                                               Function<T, CompletableFuture<U>> mapper) {
        return values.stream()
                .map(cf -> cf.thenCompose(mapper::apply))
                .collect(Collectors.toList());
    }

}
