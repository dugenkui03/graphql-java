package graphql.language

import graphql.language.node.Argument
import graphql.language.node.ArrayValue
import graphql.language.node.BooleanValue
import graphql.language.node.Directive
import graphql.language.node.NonNullType
import graphql.language.node.NullValue
import graphql.language.node.ObjectField
import graphql.language.node.ObjectValue
import graphql.language.node.SelectionSet
import graphql.language.node.TypeName
import graphql.language.node.VariableReference
import graphql.language.node.definition.DirectiveDefinition
import graphql.language.node.DirectiveLocation
import graphql.language.node.definition.EnumTypeDefinition
import graphql.language.node.EnumValue
import graphql.language.node.definition.EnumValueDefinition
import graphql.language.node.Field
import graphql.language.node.definition.FieldDefinition
import graphql.language.node.FloatValue
import graphql.language.node.definition.FragmentDefinition
import graphql.language.node.FragmentSpread
import graphql.language.node.InlineFragment
import graphql.language.node.definition.InputObjectTypeDefinition
import graphql.language.node.definition.InputValueDefinition
import graphql.language.node.IntValue
import graphql.language.node.definition.InterfaceTypeDefinition
import graphql.language.node.ListType
import graphql.language.node.StringValue
import graphql.language.node.definition.ObjectTypeDefinition
import graphql.language.node.definition.OperationDefinition
import graphql.language.node.definition.OperationTypeDefinition
import graphql.language.node.definition.ScalarTypeDefinition
import graphql.language.node.definition.SchemaDefinition
import graphql.language.node.definition.UnionTypeDefinition
import graphql.language.node.definition.VariableDefinition
import graphql.language.traverser.NodeVisitorStub
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.Collections.emptyList

class NodeVisitorStubTest extends Specification {


    @Unroll
    def "#visitMethod call visitSelection by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitSelection(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                                       | visitMethod
        Field.newField().build()                   | 'visitField'
        FragmentSpread.newFragmentSpread().build() | 'visitFragmentSpread'
        InlineFragment.newInlineFragment().build() | 'visitInlineFragment'

    }

    @Unroll
    def "#visitMethod call visitValue by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitValue(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                                             | visitMethod
        FloatValue.newFloatValue().build() | 'visitFloatValue'
        ArrayValue.newArrayValue().build() | 'visitArrayValue'
        IntValue.newIntValue().build()     | 'visitIntValue'
        new BooleanValue(true)             | 'visitBooleanValue'
        NullValue.newNullValue().build()     | 'visitNullValue'
        ObjectValue.newObjectValue().build() | 'visitObjectValue'
        VariableReference.newVariableReference().build() | 'visitVariableReference'
        EnumValue.newEnumValue().build()   | 'visitEnumValue'
        StringValue.newStringValue().build() | 'visitStringValue'
    }

    @Unroll
    def "#visitMethod call visitDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitDefinition(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT


        where:
        node                                                 | visitMethod
        OperationDefinition.newOperationDefinition().build() | 'visitOperationDefinition'
        FragmentDefinition.newFragmentDefinition().build() | 'visitFragmentDefinition'
        new DirectiveDefinition("")                        | 'visitDirectiveDefinition'
        SchemaDefinition.newSchemaDefinition().build() | 'visitSchemaDefinition'
    }

    @Unroll
    def "#visitMethod call visitTypeDefinition by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitTypeDefinition(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                                                         | visitMethod
        new UnionTypeDefinition("")  | 'visitUnionTypeDefinition'
        InputObjectTypeDefinition.newInputObjectDefinition().build() | 'visitInputObjectTypeDefinition'
        new ScalarTypeDefinition("") | 'visitScalarTypeDefinition'
        new InterfaceTypeDefinition("")                              | 'visitInterfaceTypeDefinition'
        new EnumTypeDefinition("")                                   | 'visitEnumTypeDefinition'
        new ObjectTypeDefinition("") | 'visitObjectTypeDefinition'
    }

    @Unroll
    def "#visitMethod call visitTypes by default"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitType(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                                 | visitMethod
        NonNullType.newNonNullType().build() | 'visitNonNullType'
        ListType.newListType().build() | 'visitListType'
        new TypeName("") | 'visitTypeName'

    }

    @Unroll
    def "default for #visitMethod is to call visitNode"() {
        given:
        NodeVisitorStub nodeVisitorStub = Spy(NodeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = nodeVisitorStub."$visitMethod"(node, context)
        then:
        1 * nodeVisitorStub.visitNode(node, context) >> TraversalControl.QUIT
        control == TraversalControl.QUIT

        where:
        node                                                         | visitMethod
        new Argument("", null) | 'visitArgument'
        new Directive("", emptyList())                         | 'visitDirective'
        new DirectiveLocation("")                              | 'visitDirectiveLocation'
        Document.newDocument().build()                         | 'visitDocument'
        new EnumValueDefinition("")                            | 'visitEnumValueDefinition'
        FieldDefinition.newFieldDefinition().build()           | 'visitFieldDefinition'
        InputValueDefinition.newInputValueDefinition().build() | 'visitInputValueDefinition'
        InputValueDefinition.newInputValueDefinition().build()       | 'visitInputValueDefinition'
        new ObjectField("", null) | 'visitObjectField'
        OperationTypeDefinition.newOperationTypeDefinition().build() | 'visitOperationTypeDefinition'
        OperationTypeDefinition.newOperationTypeDefinition().build() | 'visitOperationTypeDefinition'
        SelectionSet.newSelectionSet().build() | 'visitSelectionSet'
        VariableDefinition.newVariableDefinition().build()           | 'visitVariableDefinition'
        new StringValue("")                                          | 'visitValue'
        OperationDefinition.newOperationDefinition().build()         | 'visitDefinition'
        new UnionTypeDefinition("")                                  | 'visitTypeDefinition'
        Field.newField().build()                                     | 'visitSelection'
        NonNullType.newNonNullType().build()                         | 'visitType'

    }

}
