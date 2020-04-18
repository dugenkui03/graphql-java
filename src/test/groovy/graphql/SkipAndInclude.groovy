package graphql

import graphql.parser.Parser
import spock.lang.Specification

class SkipAndInclude extends Specification {

    private def graphQL = TestUtil.graphQL("""
            type Query {
                field: Int
                fieldX: Int
                fieldY(x: Int): Int
                #todo 还真没有
                fieldZ(x: Float): Int
                fieldA(if: Float): Int
            }
        """).build()

    def "@skip and @include"() {
        when:
            String str=
                    '''
                    //夸实体指令的json定义方式
                    query @spanCal(arg : "{\\"ext1\\":\\"fieldx+fieldy\\"}" ){
                        fieldx
                        fieldy
                    }
                    '''
        print(str)
        def document=new Parser().parseDocument(str);

        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkipAndInclude($skip: Boolean!, $include: Boolean!,$arg: Int!) {
                        field @skip(if: $skip) @include(if: $include)
                        fieldY(x:$arg)
                    }@aviator(exp=\"a+b\",valueKey=\"sum\"")   
                    ''')
                .variables([skip: skip, include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | include | queried
        true  | true    | false
        true  | false   | false
        false | true    | true
        false | false   | false

    }

    def "@skip"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkip($skip: Boolean!) {
                        field @skip(if: $skip)
                        fieldX
                    }   
                    ''')
                .variables([skip: skip])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        skip  | queried
        true  | false
        false | true
    }

    def "@include"() {
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithInclude($include: Boolean!) {
                        field @include(if: $include)
                        fieldX
                    }   
                    ''')
                .variables([include: include])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        ((Map) executionResult.data).containsKey("field") == queried

        where:
        include | queried
        true    | true
        false   | false
    }
}
