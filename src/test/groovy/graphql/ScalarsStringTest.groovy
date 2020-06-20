package graphql

import graphql.language.node.BooleanValue
import graphql.language.node.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.Scalars
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsStringTest extends Specification {


    @Shared
    def customObject = new Object() {
        @Override
        String toString() {
            return "foo"
        }
    }

    @Unroll
    def "String parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result

        where:
        literal                   | result
        new StringValue("1234ab") | "1234ab"

    }

    @Unroll
    def "String returns null for invalid #literal"() {
        when:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
    }

    @Unroll
    def "String serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result
        Scalars.GraphQLString.getCoercing().parseValue(value) == result

        where:
        value        | result
        "123ab"      | "123ab"
        123          | "123"
        customObject | "foo"
    }


}
