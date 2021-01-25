package readme.dataloader;

import graphql.ExecutionInput;

import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.stats.Statistics;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class GoodBatchingExamples {

    public static void main(String[] args) {
        // tag 1: 允许缓存和批量加载
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setCachingEnabled(true)
                .setCacheMap(CacheMap.simpleMap())
                .setBatchingEnabled(true)
                // tag 分片控制
                .setMaxBatchSize(2);

        // tag 2: 使用批量加载函数和配置，生成dataLoader
        DataLoader<Integer, Map> itemDataLoader = DataLoader.newDataLoader(new ItemLoaderFunc(), options);

        // tag 4: 注册dataLoader
        // todo key应该是 业务方+字段坐标？
        DataLoaderRegistry registry = new DataLoaderRegistry().register("itemIdLoader", itemDataLoader);

        Map<String, Object> userIds = Collections.singletonMap("userIds", Arrays.asList(1, 2, 3));
        ExecutionInput input = ExecutionInput.newExecutionInput().query("query($userIds: [Int]){\n" +
                "            userInfoList(userIds:$userIds){\n" +
                "                id\n" +
                "                name\n" +
                "                mostLikeItem{\n" +
                "                        id \n" +
                "                        itemName\n" +
                "                }" +
                "            }\n" +
                "        }").dataLoaderRegistry(registry).variables(userIds).build();

        // tag 请求到了 loader，进行批量加载
        Object data = EngineHolder.getInstance().execute(input).getData();
        System.out.println(data);

        ExecutionInput input2 = input.transform(
                in -> in.variables(Collections.singletonMap("userIds", Arrays.asList(2, 3, 4, 5)))
        );

        // tag 命中缓存 * 2
        Object cachedData = EngineHolder.getInstance().execute(input2).getData();
        Statistics statistics = registry.getDataLoader("itemIdLoader").getStatistics();
        System.out.println(statistics.getCacheHitCount());
    }
}