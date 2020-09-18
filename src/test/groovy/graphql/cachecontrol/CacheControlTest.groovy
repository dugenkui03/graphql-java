package graphql.cachecontrol


import graphql.ExecutionResultImpl
import graphql.TestUtil
import graphql.execution.ResultPath
import graphql.schema.DataFetcher
import spock.lang.Specification

class CacheControlTest extends Specification {

    def "can build up hints when there is no extensions present"() {
        def cacheControl = CacheControl.newCacheControl()
        // 添加元素，默认范围是 Scope.PUBLIC
        cacheControl.hint(ResultPath.parse("/hint/99"), 99)
        cacheControl.hint(ResultPath.parse("/hint/66"), 66)
        cacheControl.hint(ResultPath.parse("/hint/33/private"), 33, CacheControl.Scope.PRIVATE)
        cacheControl.hint(ResultPath.parse("/hint/private"), CacheControl.Scope.PRIVATE)

        def executionResult = ExecutionResultImpl.newExecutionResult().data("data").build()

        when:
        // 将 cacheControl 添加到 executionResult 的额外数据中
        def newER = cacheControl.addTo(executionResult)
        then:
        newER.data == "data" // left alone
        newER.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                                [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                                [path: ["hint", "33", "private"], maxAge: 33, scope: "PRIVATE"],
                                [path: ["hint", "private"], scope: "PRIVATE"],
                        ]
                ]
        ]

    }

    def "can build up hints when extensions are present"() {
        def cacheControl = CacheControl.newCacheControl()
        cacheControl.hint(ResultPath.parse("/hint/99"), 99)
        cacheControl.hint(ResultPath.parse("/hint/66"), 66)

        // 其他额外的数据
        def startingExtensions = ["someExistingExt": "data"]

        def executionResult = ExecutionResultImpl.newExecutionResult().data("data").extensions(startingExtensions).build()

        when:
        // 将cacheControl添加到额外数据
        def newER = cacheControl.addTo(executionResult)
        then:
        newER.data == "data" // left alone
        newER.extensions.size() == 2
        newER.extensions["someExistingExt"] == "data"
        newER.extensions["cacheControl"] == [
                version: 1,
                hints  : [
                        [path: ["hint", "99"], maxAge: 99, scope: "PUBLIC"],
                        [path: ["hint", "66"], maxAge: 66, scope: "PUBLIC"],
                ]
        ]
    }

    def "integration test of cache control"() {
        def sdl = '''
            type Query {
                levelA : LevelB
            }
            
            type LevelB {
                levelB : LevelC
            }
            
            type LevelC {
                levelC : String
            }
        '''

        DataFetcher dfA = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, 100)
        }
        DataFetcher dfB = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, 999)
        }

        DataFetcher dfC = { env ->
            CacheControl cc = env.getContext()
            cc.hint(env, CacheControl.Scope.PRIVATE)
        }

        // 将字段和dataFetcher相绑定
        def graphQL = TestUtil.graphQL(sdl, [
                Query : [levelA: dfA,],
                LevelB: [levelB: dfB],
                LevelC: [levelC: dfC]
        ]).build()

        /**
         * fixme: 使用方式：逻辑要写在dataFetcher中
         *      1. 创建空的cacheControl对象；
         *      2. 执行查询，dataFetcher中包含了其解析字段的缓存路径和缓存域；
         *      3. 将数据addTo到执行结果：注意，不是自动添加到结果中的
         */

        // step_1
        def cacheControl = CacheControl.newCacheControl()
        when:
        // step_2
        def er = graphQL.execute({ input ->
            input.context(cacheControl)
                    .query(' { levelA { levelB { levelC } } }')
        })
        // step_3
        er = cacheControl.addTo(er)
        then:
        er.errors.isEmpty()
        // 结果验证
        er.extensions == [
                cacheControl: [
                        version: 1,
                        hints  : [
                                [path: ["levelA"], maxAge: 100, scope: "PUBLIC"],
                                [path: ["levelA", "levelB"], maxAge: 999, scope: "PUBLIC"],
                                [path: ["levelA", "levelB", "levelC"], scope: "PRIVATE"],
                        ]
                ]
        ]
    }
}
