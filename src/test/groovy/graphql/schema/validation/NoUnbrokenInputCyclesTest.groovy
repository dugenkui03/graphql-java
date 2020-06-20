package graphql.schema.validation

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.validation.exception.SchemaValidationErrorCollector
import graphql.schema.validation.exception.SchemaValidationErrorType
import graphql.schema.validation.rules.NonNullInputObjectCyclesRuler
import spock.lang.Specification

import static graphql.schema.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeReference.typeRef

class NoUnbrokenInputCyclesTest extends Specification {

    SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

    /**
     * fixme 输入对象的相互引用不能形成一个环
     * @return
     */
    def "infinitely recursive input type results in error"() {
        given:
        //创建一个input类型对象
        GraphQLInputObjectType PersonInputType = newInputObject()
                //输入对象名称
                .name("Person")
                //输入对象字段
                .field(
                        //输入对象定义
                        GraphQLInputObjectField.newInputObjectField()
                                //字段名称
                                .name("pet")
                                //字段类型、就是现在定义的PersonInputType
                                .type(typeRef("Pet"))
                                .build()
                )
                .build()

        GraphQLInputObjectType Pet = newInputObject()
                //输入对象名称
                .name("Pet")
                //输入对象字段
                .field(
                        //输入对象定义
                        GraphQLInputObjectField.newInputObjectField()
                        //字段名称
                                .name("owner")
                        //字段类型、就是现在定义的PersonInputType
                                .type(typeRef("Person"))
                                .build()
                )
                .build()

        //字段定义
        GraphQLFieldDefinition field = newFieldDefinition()
                //类型字段名称
                .name("exists")
                //类型字段类型
                .type(GraphQLBoolean)
                //类型字段参数：值是某个参数的builder
                .argument(
                        //类型字段参数定义
                        GraphQLArgument.newArgument()
                                //参数名称
                                .name("person")
                                //参数类型
                                .type(PersonInputType)
                )
                .build()

        PersonInputType.getFieldDefinition("pet").replacedType = nonNull(Pet)
        Pet.getFieldDefinition("owner").replacedType = nonNull(PersonInputType)
        when:
        new NonNullInputObjectCyclesRuler().check(field, errorCollector)
        then:
        print(errorCollector.getErrors())
        errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }
}
