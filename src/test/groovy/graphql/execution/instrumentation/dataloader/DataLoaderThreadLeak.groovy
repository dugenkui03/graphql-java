package graphql.execution.instrumentation.dataloader

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetchingEnvironment
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import static graphql.ExecutionInput.newExecutionInput
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataLoaderThreadLeak extends Specification {
    def "deadlock attempt"() {
        setup:
        def sdl = """
        type Toy {
            name: String
        }
        
        type Cat {
            name: String
            toys: [Toy]
        }
        
        type Nation {
            name: String
        }
        
        type Owner {
            name: String
            nation: Nation
        }
        
        type Dog {
            name: String
            owner: Owner
        }
        
        type Pets {
            cats: [Cat]
            dogs: [Dog]
        }
        
        type Query {
            pets: Pets
        }
        """

        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("resolver-chain-thread-%d").build()

        def executor = new ThreadPoolExecutor(15, Integer.MAX_VALUE, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy())

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("pets", new StaticDataFetcher(['cats'  : [['name': "cat-1"], ['name': "cat-2"]]
                                                                    , 'dogs': [['name': "dog-1"], ['name': "dog-2"]]
                        ])))
                .type(newTypeWiring("Dog")
                        .dataFetcher("owner", { env -> env.getDataLoader("dog.owner").load(env) }))
                .type(newTypeWiring("Owner")
                        .dataFetcher("nation", { env -> env.getDataLoader("owner.nation").load(env) }))
                .build()

        def schema = TestUtil.schema(sdl, wiring)

        when:
        def graphql = GraphQL.newGraphQL(schema)
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()

        then: "execution shouldn't hang"
        DataLoaderRegistry dataLoaderRegistry = mkNewDataLoaderRegistry(executor)

        def result = graphql.executeAsync(newExecutionInput()
                .dataLoaderRegistry(dataLoaderRegistry)
                .query("""
                query LoadPets {
                      pets {
                        cats {
                          name
                        }
                        
                        # 添加上对dog的请求后，就回出现线程泄漏
                        dogs {
                          name
                          # 使用了dataLoader：dog.owner
                          owner {
                            name
                            # 使用了dataLoader：owner.nation
                            nation {
                              name
                            }
                          }
                        }
                      }
                    }
                    """).build())

        result.whenComplete({ res, error ->
            if (error) {
                throw error
            }
            assert res.errors.empty
        })

        // wait for each future to complete and grab the results
        result.whenComplete({ results, error ->
            if (error) {
                throw error
            }
            results.each { assert it.errors.empty }
        }).join()
    }

    private static DataLoaderRegistry mkNewDataLoaderRegistry(executor) {
        def dataLoaderRegistry = new DataLoaderRegistry()

        def dataLoaderNations = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                List<Map> nationInfo = new ArrayList<>();
                nationInfo.add([['name': "nation-name-1"]])
                return nationInfo
            }
        }, DataLoaderOptions.newOptions())
        dataLoaderRegistry.register("owner.nation", dataLoaderNations)

        def dataLoaderOwners = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                List<Map> ownerInfo = new ArrayList<>();
                ownerInfo.add([['name': "owner-name-1"]])
                return ownerInfo
            }
        }, DataLoaderOptions.newOptions())
        dataLoaderRegistry.register("dog.owner", dataLoaderOwners)

        dataLoaderRegistry
    }
}