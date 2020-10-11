package graphql.execution.instrumentation.dataloader

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
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
        type Nation {
            name: String
        }
        
        type Toy {
            name: String
        }
        
        type Owner {
            name: String
            nation: Nation
        }
        
        type Cat {
            name: String
            toys: [Toy]
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

        def cats = [['id': "cat-1", 'name': "cat-1"], ['id': "cat-2", 'name': "cat-2"]]
        def dogs = [['id': "dog-1", 'name': "dog-1"], ['id': "dog-2", 'name': "dog-2"]]

        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("resolver-chain-thread-%d").build()

        def executor = new ThreadPoolExecutor(15, 15, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy())

        DataFetcher nationsDf = { env -> env.getDataLoader("owner.nation").load(env) } as DataFetcher
        DataFetcher ownersDf = { env -> env.getDataLoader("dog.owner").load(env) } as DataFetcher

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("pets", new StaticDataFetcher(['cats': cats, 'dogs': dogs])))
                .type(newTypeWiring("Cat")
                        .dataFetcher("toys", new StaticDataFetcher(new List<Object>() {
                            @Override
                            int size() {
                                return 1
                            }

                            @Override
                            boolean isEmpty() {
                                return false
                            }

                            @Override
                            boolean contains(Object o) {
                                return false
                            }

                            @Override
                            Iterator iterator() {
                                throw new RuntimeException()
                            }

                            @Override
                            Object[] toArray() {
                                return new Object[0]
                            }

                            @Override
                            Object[] toArray(Object[] a) {
                                return null
                            }

                            @Override
                            boolean add(Object o) {
                                return false
                            }

                            @Override
                            boolean remove(Object o) {
                                return false
                            }

                            @Override
                            boolean containsAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean addAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean addAll(int index, Collection c) {
                                return false
                            }

                            @Override
                            boolean removeAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean retainAll(Collection c) {
                                return false
                            }

                            @Override
                            void clear() {

                            }

                            @Override
                            Object get(int index) {
                                return null
                            }

                            @Override
                            Object set(int index, Object element) {
                                return null
                            }

                            @Override
                            void add(int index, Object element) {

                            }

                            @Override
                            Object remove(int index) {
                                return null
                            }

                            @Override
                            int indexOf(Object o) {
                                return 0
                            }

                            @Override
                            int lastIndexOf(Object o) {
                                return 0
                            }

                            @Override
                            ListIterator listIterator() {
                                return null
                            }

                            @Override
                            ListIterator listIterator(int index) {
                                return null
                            }

                            @Override
                            List subList(int fromIndex, int toIndex) {
                                return null
                            }
                        })))
                .type(newTypeWiring("Dog")
                        .dataFetcher("owner", ownersDf))
                .type(newTypeWiring("Owner")
                        .dataFetcher("nation", nationsDf))
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
                          toys {
                            name
                          }
                        }
                        dogs {
                          name
                          owner {
                            name
                            nation {
                              name
                            }
                          }
                        }
                      }
                    }
                    """)
                .build())

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
        })
        /**
         * join()执行不了了，报错信息
         * "Test worker" #11 prio=5 os_prio=31 tid=0x00007fd17c832000 nid=0x5503 waiting on condition [0x000070000d3c4000]
         *    java.lang.Thread.State: WAITING (parking)
         * 	at sun.misc.Unsafe.park(Native Method)
         * 	- parking to wait for  <0x00000007b64b1e88> (a java.util.concurrent.CompletableFuture$Signaller)
         * 	at java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)
         * 	at java.util.concurrent.CompletableFuture$Signaller.block(CompletableFuture.java:1707)
         * 	at java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3323)
         * 	at java.util.concurrent.CompletableFuture.waitingGet(CompletableFuture.java:1742)
         * 	at java.util.concurrent.CompletableFuture.join(CompletableFuture.java:1947)
         */
                .join()
    }

    //        DataFetcher nationsDf = { env -> env.getDataLoader("owner.nation").load(env) } as DataFetcher
    //        DataFetcher ownersDf = { env -> env.getDataLoader("dog.owner").load(env) } as DataFetcher
    // owner.nation
    // dog.owner
    private static DataLoaderRegistry mkNewDataLoaderRegistry(executor) {
        def dataLoaderNations = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def nations = []
                    for (int i = 1; i <= 2; i++) {
                        nations.add(['id': "nation-$i", 'name': "nation-$i"])
                    }
                    return nations
                }, executor)
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderOwners = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def owners = []
                    for (int i = 1; i <= 2; i++) {
                        owners.add(['id': "owner-$i", 'name': "owner-$i"])
                    }
                    return owners
                }, executor)
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("dog.owner", dataLoaderOwners)
        dataLoaderRegistry.register("owner.nation", dataLoaderNations)
        dataLoaderRegistry
    }
}