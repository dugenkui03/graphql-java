package readme;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.TestUtil;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.DataFetcher;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GoodBatchingExamples {



    public static void main(String[] args) {

        BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> {
                    System.out.println("invoke dataLoader, keys = "+keys.toString());
                    return Arrays.asList("partnerName * "+keys.size());
                });
            }
        };


        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(characterBatchLoader);

        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", characterDataLoader);

        String spec = " type Query {\n" +
                "                userInfo: [User]\n" +
                "            }\n" +
                "\n" +
                "            type User{\n" +
                "                id: Int\n" +
                "                name: String\n" +
                "                friendName: String\n" +
                "            }";

        DataFetcher  userInfoDataFetcher =  dataFetchingEnvironment ->{
            Map<String, Object> userInfo1 = new HashMap<>();
            userInfo1.put("id", 130);
            userInfo1.put("name", "du");

            Map<String, Object> userInfo2 = new HashMap<>();
            userInfo2.put("id", 434);
            userInfo2.put("name", "gen");

            Map<String, Object> userInfo3 = new HashMap<>();
            userInfo3.put("id", 1991);
            userInfo3.put("name", "kui");
            return Arrays.asList(userInfo1,userInfo2,userInfo3);
        };


        DataFetcher partnerNameDataFetcher = env -> {
            System.out.println("invoke partnerNameDataFetcher");
            DataLoader<String, Object> dataLoader = env.getDataLoader("character");

            /**
             * fixme key相同的时候、后续的调用都会命中缓存
             *       因此批量加载的key个数只有一个。
             */
            return dataLoader.load("2001"); // R2D2
        };

        Map<String,DataFetcher> d1 = new HashMap<>();
        d1.put("userInfo",userInfoDataFetcher);

        Map<String,DataFetcher> d2 = new HashMap<>();
        d2.put("friendName",partnerNameDataFetcher);

        Map<String, Map<String, DataFetcher>> graphqlARG = new HashMap<>();
        graphqlARG.put("Query",d1);
        graphqlARG.put("User",d2);


        DataLoaderDispatcherInstrumentationOptions options = DataLoaderDispatcherInstrumentationOptions
                .newOptions().includeStatistics(true);

        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(options);

        GraphQL graphQL = TestUtil.graphQL(spec, graphqlARG).instrumentation(dispatcherInstrumentation).build();


        ExecutionInput input = ExecutionInput.newExecutionInput().query("query{\n" +
                "            userInfo{\n" +
                "                id\n" +
                "                        name\n" +
                "                friendName\n" +
                "            }\n" +
                "        }").dataLoaderRegistry(registry).build();

        Object data = graphQL.execute(input).getData();
        System.out.println(data);
    }
}
