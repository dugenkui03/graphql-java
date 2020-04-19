package graphql.schema.validation

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.validation.exception.SchemaValidationErrorCollector
import graphql.schema.validation.rules.NoUnbrokenInputCyclesRule
import graphql.schema.validation.exception.SchemaValidationErrorType

import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeReference.typeRef

class NoUnbrokenInputCyclesTest extends Specification {

    SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

    def "infinitely recursive input type results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("friend")
                .type(typeRef("Person"))
                .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                .name("person")
                .type(PersonInputType))
                .build()

        PersonInputType.getFieldDefinition("friend").replacedType = nonNull(PersonInputType)
        when:
        new NoUnbrokenInputCyclesRule().checkFieldDefinition(field, errorCollector)
        then:
        errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }
}
