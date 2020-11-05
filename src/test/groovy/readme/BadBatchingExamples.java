package readme;

import graphql.GraphQL;
import graphql.TestUtil;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.DataFetcher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BadBatchingExamples {
    public static void main(String[] args) {
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
            // todo 使用dataLoader？？
            return "partnerName";
        };

        Map<String,DataFetcher> d1 = new HashMap<>();
        d1.put("userInfo",userInfoDataFetcher);

        Map<String,DataFetcher> d2 = new HashMap<>();
        d2.put("friendName",partnerNameDataFetcher);

        Map<String, Map<String, DataFetcher>> graphqlARG = new HashMap<>();
        graphqlARG.put("Query",d1);
        graphqlARG.put("User",d2);

        GraphQL graphQL = TestUtil.graphQL(spec, graphqlARG).build();

        Object data = graphQL.execute("query{\n" +
                "            userInfo{\n" +
                "                id\n" +
                "                        name\n" +
                "                friendName\n" +
                "            }\n" +
                "        }").getData();
        System.out.println(data);
    }
}
