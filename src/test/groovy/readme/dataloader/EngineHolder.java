package readme.dataloader;

import java.util.HashMap;
import java.util.Map;

import graphql.GraphQL;
import graphql.TestUtil;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.DataFetcher;

/**
 **/
public class EngineHolder {

    private static GraphQL graphQL;

    public static GraphQL getInstance() {
        return graphQL;
    }

    private static String schemaSpec =
            "            type Query {\n" +
                    "                userInfoList(userIds: [Int]): [User]\n" +
                    "            }\n" +
                    "" +
                    "            type User{\n" +
                    "                id: Int\n" +
                    "                name: String\n" +
                    // tag 1: 单独绑定了一个 dataFetcher
                    //        没有dataLoader的情况，每遍历该元素都会请求一次
                    //         使用dataLoader后，期望能够批量加载并缓存
                    "                mostLikeItem(itemId: Int): Item\n" +
                    "            }\n" +
                    "" +
                    "            type Item{\n" +
                    "                id: Int\n" +
                    "                itemName: String\n" +
                    "            }";


    // tag 配置dataFetcher
    static {
        DataLoaderDispatcherInstrumentationOptions options = DataLoaderDispatcherInstrumentationOptions
                .newOptions().includeStatistics(true);
        DataLoaderDispatcherInstrumentation dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(options);
        Map<String, DataFetcher> d1 = new HashMap<>();
        d1.put("userInfoList", new UserInfoFetcher());

        Map<String, DataFetcher> d2 = new HashMap<>();
        d2.put("mostLikeItem", new ItemInfoFetcher());

        Map<String, Map<String, DataFetcher>> dfConfig = new HashMap<>();
        dfConfig.put("Query", d1);
        dfConfig.put("User", d2);
        graphQL = TestUtil.graphQL(schemaSpec, dfConfig).instrumentation(dispatcherInstrumentation).build();
    }

}
