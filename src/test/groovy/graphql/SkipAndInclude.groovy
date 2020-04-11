package graphql

import spock.lang.Specification

class SkipAndInclude extends Specification {

    private def graphQL = TestUtil.graphQL("""
            type _Query {
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
        def executionInput = ExecutionInput.newExecutionInput()
                .query('''
                    query QueryWithSkipAndInclude($skip: Boolean!, $include: Boolean!) {
                        field @skip(if: $skip) @include(if: $include)
                        fieldY(x:1,x:2)
                    }   
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
