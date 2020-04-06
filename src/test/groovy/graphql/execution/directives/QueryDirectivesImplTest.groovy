package graphql.execution.directives

import graphql.TestUtil
import graphql.execution.MergedField
import spock.lang.Specification

/**
 * TODO 重要，查询指令
 */
class QueryDirectivesImplTest extends Specification {

    def sdl = '''
        # 指令名称是timeout；指令参数是afterMillis、指令参数类型是Int；指令定义在查询字段上
        directive @timeout(afterMillis : Int) on FIELD
        
        # 缓存时间
        directive @cached(forMillis : Int = 99) on FIELD | QUERY
        
        directive @upper(place : String) on FIELD
 
        type Query {
            books(searchString : String) : [Book]
        }
        
        type Book {
         id :  ID
         title : String
         __review : String
        }
    '''

    def schema = TestUtil.schema(sdl)


    def "can get immediate directives"() {

        def f1 = TestUtil.parseField("f1 @cached @upper")
        def f2 = TestUtil.parseField("f2 @cached(forMillis : \$var) @timeout")

        def mergedField = MergedField.newMergedField([f1, f2]).build()

        //[var: 10]定义了一个Map对象
        def impl = new QueryDirectivesImpl(mergedField, schema, [var: 10])

        when:
        def directives = impl.getImmediateDirectivesByName()
        then:
        directives.keySet().sort() == ["cached", "timeout", "upper"]

        when:
        def result = impl.getImmediateDirective("cached")

        then:
        result.size() == 2
        result[0].getName() == "cached"
        result[1].getName() == "cached"

        result[0].getArgument("forMillis").getValue() == 99 // defaults
        result[0].getArgument("forMillis").getDefaultValue() == 99

        result[1].getArgument("forMillis").getValue() == 10
        result[1].getArgument("forMillis").getDefaultValue() == 99

        // the prototypical other properties are copied ok
        result[0].validLocations().collect({ it.name() }).sort() == ["FIELD", "QUERY"]
        result[1].validLocations().collect({ it.name() }).sort() == ["FIELD", "QUERY"]
    }

}
