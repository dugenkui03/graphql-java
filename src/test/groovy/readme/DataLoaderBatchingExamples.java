package readme;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderContextProvider;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static graphql.ExecutionInput.newExecutionInput;

@SuppressWarnings({"unused", "Convert2Lambda", "ConstantConditions", "ClassCanBeStatic"})
public class DataLoaderBatchingExamples {

    // 星球大战人物
    class StarWarsCharacter {
        /**
         * 获取该人物的 friendId 列表
         */
        List<String> getFriendIds() {
            return null;
        }
    }

    /**
     *  redis 类型数据源：
     *      1. 设置、获取、是否包含、清空
     */
    class Redis {

        public boolean containsKey(String key) {
            return false;
        }

        public Object getValue(String key) {
            return null;
        }

        public CacheMap<String, Object> setValue(String key, Object value) {
            return null;
        }

        public void clearKey(String key) {
        }

        public CacheMap<String, Object> clearAll() {
            return null;
        }
    }

    /**
     * 安全上下文：保存token
     */
    static class SecurityContext {

        static SecurityContext newSecurityContext() {
            return null;
        }

        // 获取token
        Object getToken() {
            return null;
        }
    }


    // redis对象
    Redis redisIntegration;

    /**
     * fixme 批量加载函数
     */
    BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
        @Override
        public CompletionStage<List<Object>> load(List<String> keys) {
            return CompletableFuture.completedFuture(null);
        }
    };


    /**
     * fixme
     *      step_1：创建 批量加载函数 BatchLoader；
     *      step_2：使用批量加载函数创建 DataLoader；
     *      step_3：构造 dataLoaser 注册器register；
     *      step_4：将该 dataLoader 加入到 注册器：指定dataLoader的名称；
     *      step_5：将 register 作为输入参数；
     *      step_6：environment.getDataLoader 获取指定名称的 dataLoader；
     *      step_7：调用dataLoader的 load()、loadMany()等方法，可选择性的附带上下文。
     */
    public static DataLoaderRegistry registry = new DataLoaderRegistry();
    static{
        // a batch loader function that will be called with N or more keys for batch loading
        // This can be a singleton object since it's stateless

        // fixme 方法里创建的批量加载对象
        BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                //
                // we use supplyAsync() of values here for maximum parellisation
                //
                return CompletableFuture.supplyAsync(() -> getCharacterDataViaBatchHTTPApi(keys));
            }
        };

        // a data loader for characters that points to the character batch loader
        // Since data loaders are stateful, they are created per execution request.
        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(characterBatchLoader);

        // DataLoaderRegistry is a place to register all data loaders in that needs to be dispatched together
        // in this case there is 1 but you can have many.
        // Also note that the data loaders are created per execution request
        registry.register("character", characterDataLoader);
    }


    // 获取查询？
    private String getQuery() {
        return null;
    }

    private static List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        return null;
    }


    public static void main(String[] args) {
        new DataLoaderBatchingExamples().starWarsExample();
    }

    void starWarsExample() {
        // fixme 英雄 dataFetcher，使用 character dataLoader
        // use this data loader in the data fetchers associated with characters
        // and put them into the graphql schema (not shown)
        DataFetcher heroDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                // fixme 获取人物类型 DataLoader
                DataLoader<String, Object> dataLoader = environment.getDataLoader("character");
                // fixme 获取2001的数据
                return dataLoader.load("2001"); // R2D2
            }
        };

        // fixme 朋友 dataFetcher，使用的dataLoader同heroDataFetcher
        DataFetcher friendsDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                /**
                 * 从父级资源获取朋友ids
                 */
                StarWarsCharacter starWarsCharacter = environment.getSource();
                List<String> friendIds = starWarsCharacter.getFriendIds();

                /**
                 * fixme 获取这些id对应的朋友列表
                 *       此处调用loadMay，上边调用 load()
                 */
                DataLoader<String, Object> dataLoader = environment.getDataLoader("character");
                return dataLoader.loadMany(friendIds);
            }
        };


        //
        // this instrumentation implementation will dispatch all the data loaders
        // as each level of the graphql query is executed and hence make batched objects
        // available to the query and the associated DataFetchers
        //
        // true表示 "是否将对 java-dataloader 的统计数据放到结果中"
        //
        DataLoaderDispatcherInstrumentationOptions options = DataLoaderDispatcherInstrumentationOptions
                .newOptions().includeStatistics(true);


        DataLoaderDispatcherInstrumentation dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(options);

        //
        // now build your graphql object and execute queries on it.
        // the data loader will be invoked via the data fetchers on the
        // schema fields
        //
        GraphQL graphQL = GraphQL.newGraphQL(buildSchema())
                .instrumentation(dispatcherInstrumentation)
                .build();

        ExecutionInput executionInput = newExecutionInput()
                .query(getQuery())
                .dataLoaderRegistry(registry)
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);
    }


    private void changeCachingSolutionOfDataLoader() {

        CacheMap<String, Object> crossRequestCacheMap = new CacheMap<String, Object>() {
            @Override
            public boolean containsKey(String key) {
                return redisIntegration.containsKey(key);
            }

            @Override
            public Object get(String key) {
                return redisIntegration.getValue(key);
            }

            @Override
            public CacheMap<String, Object> set(String key, Object value) {
                redisIntegration.setValue(key, value);
                return this;
            }

            @Override
            public CacheMap<String, Object> delete(String key) {
                redisIntegration.clearKey(key);
                return this;
            }

            @Override
            public CacheMap<String, Object> clear() {
                redisIntegration.clearAll();
                return this;
            }
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(crossRequestCacheMap);

        DataLoader<String, Object> dataLoader = DataLoader.newDataLoader(batchLoader, options);
    }

    private void doNotUseAsyncInYouDataFetcher() {

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.completedFuture(getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoader);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // Don't DO THIS!
                //
                return CompletableFuture.supplyAsync(() -> {
                    String argId = environment.getArgument("id");
                    DataLoader<String, Object> characterLoader = environment.getDataLoader("characterLoader");
                    return characterLoader.load(argId);
                });
            }
        };
    }

    private void doAsyncInYourBatchLoader() {

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoader);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // This is OK
                //
                String argId = environment.getArgument("id");
                DataLoader<String, Object> characterLoader = environment.getDataLoader("characterLoader");
                return characterLoader.load(argId);
            }
        };
    }

    private void passingContextToYourBatchLoader() {

        BatchLoaderWithContext<String, Object> batchLoaderWithCtx = new BatchLoaderWithContext<String, Object>() {

            @Override
            public CompletionStage<List<Object>> load(List<String> keys, BatchLoaderEnvironment loaderContext) {
                //
                // we can have an overall context object
                SecurityContext securityCtx = loaderContext.getContext();
                //
                // and we can have a per key set of context objects
                Map<Object, Object> keysToSourceObjects = loaderContext.getKeyContexts();

                return CompletableFuture.supplyAsync(() -> getTheseCharacters(securityCtx.getToken(), keys, keysToSourceObjects));
            }
        };

        // ....

        SecurityContext securityCtx = SecurityContext.newSecurityContext();

        BatchLoaderContextProvider contextProvider = new BatchLoaderContextProvider() {
            @Override
            public Object getContext() {
                return securityCtx;
            }
        };
        //
        // this creates an overall context for the dataloader
        //
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setBatchLoaderContextProvider(contextProvider);
        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoaderWithCtx, loaderOptions);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                String argId = environment.getArgument("id");
                Object source = environment.getSource();
                //
                // you can pass per load call contexts
                //
                return characterDataLoader.load(argId, source);
            }
        };
    }


    private List<Object> getTheseCharacters(List<String> keys) {
        return null;
    }

    private List<Object> getTheseCharacters(Object token, List<String> keys, Object sources) {
        return null;
    }

    private GraphQLSchema staticSchema_Or_MayBeFrom_IoC_Injection() {
        return null;
    }

    private <K, V> DataLoader<K, V> getCharacterDataLoader() {
        return null;
    }

    private GraphQLSchema buildSchema() {
        return null;
    }

}
