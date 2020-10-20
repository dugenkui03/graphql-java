package graphql.cachecontrol;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.ResultPath;
import graphql.schema.DataFetchingEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.util.FpKit.map;

/**todo：?是对dataFetcher缓存的 字段信息 的描述？
 * This class implements the graphql Cache Control specification
 * as outlined in https://github.com/apollographql/apollo-cache-control
 * <p>
 * To best use this class you need to pass a CacheControl object to each {@link graphql.schema.DataFetcher}
 * and have them decide on the caching hint values.
 * fixme
 *      对 apollo缓存控制规范 的实现。
 *      该类最佳实践是将 CacheControl 对象传递给DataFetcher，让 CacheControl 缓存对象执行缓存策略、或不缓存
 *      apollo对该功能的实现：https://www.apollographql.com/docs/apollo-server/performance/caching/
 * <p>
 * The easiest why to do this is create a CacheControl object at query start
 * and pass it in as a "context" object via {@link graphql.ExecutionInput#getContext()}
 * and then have each {@link graphql.schema.DataFetcher} thats wants to make cache control hints use that.
 * todo：描述有误会： why -> way； CacheControl现在也变成了输入对象的一个字段
 *
 * <p>
 * Then at the end of the query you would call {@link #addTo(graphql.ExecutionResult)}
 * to record the cache control hints into the {@link graphql.ExecutionResult}
 * extensions map as per the specification.
 */
@PublicApi
public class CacheControl {

    /**
     * If the scope is set to PRIVATE, this indicates anything under this path should only be cached per-user,
     * unless the value is overridden on a sub path.
     * PUBLIC is the default and means anything under this path can be stored in a shared cache.
     *
     * fixme
     *      private表示每个用户一份缓存；
     *      public表示该路径下所有用户共享一份缓存；
     */
    public enum Scope {
        PUBLIC, PRIVATE
    }


    private class Hint {
        private final List<Object> path;
        private final Integer maxAge;
        private final Scope scope;

        private Hint(List<Object> path, Integer maxAge, Scope scope) {
            assertNotEmpty(path);
            this.path = path;
            this.maxAge = maxAge;
            this.scope = scope;
        }

        //将当前Hint对象转为Map对象
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            if (maxAge != null) {
                map.put("maxAge", maxAge);
            }
            if (scope != null) {
                map.put("scope", scope.name());
            }
            return map;
        }
    }

    private final List<Hint> hints;

    public static final String CACHE_CONTROL_EXTENSION_KEY = "cacheControl";

    // https://juejin.im/post/6844903576339218440
    // 在计算机中就是当你想要对一块内存进行修改时，我们不在原有内存块中进行写操作，而是将内存拷贝一份，
    // 在新的内存中进行写操作，写完之后呢，就将指向原来内存指针指向新的内存，原来的内存就可以被回收掉嘛！
    private CacheControl() {
        hints = new CopyOnWriteArrayList<>();
    }


    /**
     * This creates a cache control hint for the specified path
     * 为某个特定的路径创建 缓存控制对象CacheControl
     *
     * @param path   the path to the field that has the cache control hint
     * @param maxAge the caching time in seconds
     * @param scope  the scope of the cache control hint
     *
     * @return this object builder style
     */
    public CacheControl hint(ResultPath path, Integer maxAge, Scope scope) {
        assertNotNull(path);
        assertNotNull(scope);
        hints.add(new Hint(path.toList(), maxAge, scope));
        return this;
    }

    /**
     * This creates a cache control hint for the specified path
     *
     * @param path  the path to the field that has the cache control hint
     * @param scope the scope of the cache control hint
     *
     * @return this object builder style
     */
    public CacheControl hint(ResultPath path, Scope scope) {
        return hint(path, null, scope);
    }

    /**
     * This creates a cache control hint for the specified path
     *
     * @param path   the path to the field that has the cache control hint
     * @param maxAge the caching time in seconds
     *
     * @return this object builder style
     */
    public CacheControl hint(ResultPath path, Integer maxAge) {
        return hint(path, maxAge, Scope.PUBLIC);
    }

    /**
     * This creates a cache control hint for the specified field being fetched
     *
     * @param dataFetchingEnvironment the path to the field that has the cache control hint
     * @param maxAge                  the caching time in seconds
     * @param scope                   the scope of the cache control hint
     *
     * @return this object builder style
     */
    public CacheControl hint(DataFetchingEnvironment dataFetchingEnvironment, Integer maxAge, Scope scope) {
        assertNotNull(dataFetchingEnvironment);
        assertNotNull(scope);
        hint(dataFetchingEnvironment.getExecutionStepInfo().getPath(), maxAge, scope);
        return this;
    }

    /**
     * This creates a cache control hint for the specified field being fetched with a PUBLIC scope
     * 为 DataFetchingEnvironment 创建 control hint，有效域为public
     *
     * @param dataFetchingEnvironment the path to the field that has the cache control hint
     * @param maxAge                  the caching time in seconds
     *
     * @return this object builder style
     */
    public CacheControl hint(DataFetchingEnvironment dataFetchingEnvironment, Integer maxAge) {
        hint(dataFetchingEnvironment, maxAge, Scope.PUBLIC);
        return this;
    }

    /**
     * This creates a cache control hint for the specified field being fetched with a specified scope
     *
     * @param dataFetchingEnvironment the path to the field that has the cache control hint
     * @param scope                   the scope of the cache control hint
     *
     * @return this object builder style
     */
    public CacheControl hint(DataFetchingEnvironment dataFetchingEnvironment, Scope scope) {
        return hint(dataFetchingEnvironment, null, scope);
    }

    /**
     * Creates a new CacheControl object that can be used to trick caching hints
     *
     * @return the new object
     */
    public static CacheControl newCacheControl() {
        return new CacheControl();
    }

    /**
     * This will record the values in the cache control object into the provided execution result object which creates a new {@link graphql.ExecutionResult}
     * object back out
     *
     * @param executionResult the starting execution result object
     *
     * @return a new execution result with the hints in the extensions map.
     */
    public ExecutionResult addTo(ExecutionResult executionResult) {
        return ExecutionResultImpl.newExecutionResult()
                                  .from(executionResult)
                                  .addExtension(CACHE_CONTROL_EXTENSION_KEY, hintsToCacheControlProperties())
                                  .build();
    }

    private Map<String, Object> hintsToCacheControlProperties() {
        List<Map<String, Object>> recordedHints = map(hints, Hint::toMap);

        Map<String, Object> cacheControl = new LinkedHashMap<>();
        cacheControl.put("version", 1);
        cacheControl.put("hints", recordedHints);
        return cacheControl;
    }
}
