package graphql.schema

import graphql.error.AssertException
import spock.lang.Specification

import static Scalars.GraphQLString

class GraphQLNonNullTest extends Specification {

    def "non null wrapping"() {
        when:
        GraphQLNonNull.nonNull(GraphQLString)
        then:
        noExceptionThrown()

        when:
        GraphQLNonNull.nonNull(GraphQLList.list(GraphQLString))
        then:
        noExceptionThrown()

        when:
        GraphQLNonNull.nonNull(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLString)))
        then:
        thrown(AssertException)
    }
}
