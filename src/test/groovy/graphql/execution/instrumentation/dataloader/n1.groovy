package graphql.execution.instrumentation.dataloader

import graphql.TestUtil
import graphql.schema.DataFetcher
import spock.lang.Specification

class n1 extends Specification{

    def "复现在一次调用中的 n +1 问题"() {
        given:
        def spec = """
            type Query {
                userInfo: [User]
            }
            
            type User{
                id: Int
                name: String
                friendName: String
            }
        """

        def userInfoDataFetcher = { dataFetchingEnvironment ->
            Map<String, Object> userInfo1 = new HashMap<>();
            userInfo1.put("id", 130)
            userInfo1.put("name", "du")

            Map<String, Object> userInfo2 = new HashMap<>();
            userInfo2.put("id", 434)
            userInfo2.put("name", "gen")

            Map<String, Object> userInfo3 = new HashMap<>();
            userInfo3.put("id", 1991)
            userInfo3.put("name", "kui")

            return [userInfo1,userInfo2,userInfo3]
        } as DataFetcher<List>

        // fixme 的确是打印了三遍、即调用了三次
        def partnerNameDataFetcher = { partnerNameDataFetcher ->
            println "调用 partnerNameDataFetcher"
            return "partnerName";
        } as DataFetcher<String>

        def graphQL = TestUtil.graphQL(spec, ["Query": ["userInfo": userInfoDataFetcher],"User":["friendName":partnerNameDataFetcher]]).build()

        when:
        def data = graphQL.execute('''
                query{
                    userInfo{
                        id
                        name
                        friendName
                    }
                }
        ''').getData();

        then:
        print(data)

        1==1
    }


    def "解决一次调用中的 n+1 问题"() {
        given:
        def spec = """
            type Query {
                userInfo: [User]
            }
            
            type User{
                id: Int
                name: String
                friendName: String
            }
        """

        def userInfoDataFetcher = { dataFetchingEnvironment ->
            Map<String, Object> userInfo1 = new HashMap<>();
            userInfo1.put("id", 130)
            userInfo1.put("name", "du")

            Map<String, Object> userInfo2 = new HashMap<>();
            userInfo2.put("id", 434)
            userInfo2.put("name", "gen")

            Map<String, Object> userInfo3 = new HashMap<>();
            userInfo3.put("id", 1991)
            userInfo3.put("name", "kui")

            return [userInfo1,userInfo2,userInfo3]
        } as DataFetcher<List>

        // fixme 这里边加 dataLoader
        def partnerNameDataFetcher = { partnerNameDataFetcher ->
            println "调用 partnerNameDataFetcher"
            return "partnerName";
        } as DataFetcher<String>

        def graphQL = TestUtil.graphQL(spec, ["Query": ["userInfo": userInfoDataFetcher],"User":["friendName":partnerNameDataFetcher]]).build()

        when:
        def data = graphQL.execute('''
                query{
                    userInfo{
                        id
                        name
                        friendName
                    }
                }
        ''').getData();

        then:
        print(data)

        1==1
    }

}
