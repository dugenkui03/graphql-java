package graphql.language.traverser;

import graphql.masker.PublicApi;
import graphql.language.Document;
import graphql.language.node.definition.SchemaDefinition;
import graphql.language.node.SelectionSet;
import graphql.language.node.TypeName;
import graphql.language.node.VariableReference;
import graphql.language.node.Argument;
import graphql.language.node.ArrayValue;
import graphql.language.node.BooleanValue;
import graphql.language.node.Directive;
import graphql.language.node.NonNullType;
import graphql.language.node.NullValue;
import graphql.language.node.ObjectField;
import graphql.language.node.ObjectValue;
import graphql.language.node.definition.DirectiveDefinition;
import graphql.language.node.DirectiveLocation;
import graphql.language.node.definition.EnumTypeDefinition;
import graphql.language.node.EnumValue;
import graphql.language.node.definition.EnumValueDefinition;
import graphql.language.node.Field;
import graphql.language.node.definition.FieldDefinition;
import graphql.language.node.FloatValue;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.FragmentSpread;
import graphql.language.node.InlineFragment;
import graphql.language.node.definition.InputObjectTypeDefinition;
import graphql.language.node.definition.InputValueDefinition;
import graphql.language.node.IntValue;
import graphql.language.node.definition.InterfaceTypeDefinition;
import graphql.language.node.ListType;
import graphql.language.node.Node;
import graphql.language.node.StringValue;
import graphql.language.node.definition.ObjectTypeDefinition;
import graphql.language.node.definition.OperationDefinition;
import graphql.language.node.definition.OperationTypeDefinition;
import graphql.language.node.definition.ScalarTypeDefinition;
import graphql.language.node.definition.UnionTypeDefinition;
import graphql.language.node.definition.VariableDefinition;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

/**
 * fixme 访问类型系统中的节点，对应查询文档节点访问者{@link graphql.analysis.QueryVisitor}
 * Used by {@link NodeTraverser} to visit {@link Node}.
 */
@PublicApi
public interface NodeVisitor {

    TraversalControl visitArgument(Argument node, TraverserContext<Node> data);

    TraversalControl visitArrayValue(ArrayValue node, TraverserContext<Node> data);

    TraversalControl visitBooleanValue(BooleanValue node, TraverserContext<Node> data);

    TraversalControl visitDirective(Directive node, TraverserContext<Node> data);

    TraversalControl visitDirectiveDefinition(DirectiveDefinition node, TraverserContext<Node> data);

    TraversalControl visitDirectiveLocation(DirectiveLocation node, TraverserContext<Node> data);

    TraversalControl visitDocument(Document node, TraverserContext<Node> data);

    TraversalControl visitEnumTypeDefinition(EnumTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitEnumValue(EnumValue node, TraverserContext<Node> data);

    TraversalControl visitEnumValueDefinition(EnumValueDefinition node, TraverserContext<Node> data);

    TraversalControl visitField(Field node, TraverserContext<Node> data);

    TraversalControl visitFieldDefinition(FieldDefinition node, TraverserContext<Node> data);

    TraversalControl visitFloatValue(FloatValue node, TraverserContext<Node> data);

    TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> data);

    TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> data);

    TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> data);

    TraversalControl visitInputObjectTypeDefinition(InputObjectTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitInputValueDefinition(InputValueDefinition node, TraverserContext<Node> data);

    TraversalControl visitIntValue(IntValue node, TraverserContext<Node> data);

    TraversalControl visitInterfaceTypeDefinition(InterfaceTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitListType(ListType node, TraverserContext<Node> data);

    TraversalControl visitNonNullType(NonNullType node, TraverserContext<Node> data);

    TraversalControl visitNullValue(NullValue node, TraverserContext<Node> data);

    TraversalControl visitObjectField(ObjectField node, TraverserContext<Node> data);

    TraversalControl visitObjectTypeDefinition(ObjectTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitObjectValue(ObjectValue node, TraverserContext<Node> data);

    TraversalControl visitOperationDefinition(OperationDefinition node, TraverserContext<Node> data);

    TraversalControl visitOperationTypeDefinition(OperationTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitScalarTypeDefinition(ScalarTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitSchemaDefinition(SchemaDefinition node, TraverserContext<Node> data);

    TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> data);

    TraversalControl visitStringValue(StringValue node, TraverserContext<Node> data);

    TraversalControl visitTypeName(TypeName node, TraverserContext<Node> data);

    TraversalControl visitUnionTypeDefinition(UnionTypeDefinition node, TraverserContext<Node> data);

    TraversalControl visitVariableDefinition(VariableDefinition node, TraverserContext<Node> data);

    TraversalControl visitVariableReference(VariableReference node, TraverserContext<Node> data);
}
