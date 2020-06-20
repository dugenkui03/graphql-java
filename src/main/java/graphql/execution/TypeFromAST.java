package graphql.execution;


import graphql.masker.Internal;
import graphql.language.node.ListType;
import graphql.language.node.NonNullType;
import graphql.language.node.Type;
import graphql.language.node.TypeName;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;

@Internal
public class TypeFromAST {


    public static GraphQLType getTypeFromAST(GraphQLSchema schema, Type type) {
        GraphQLType innerType;
        if (type instanceof ListType) {
            innerType = getTypeFromAST(schema, ((ListType) type).getType());
            return innerType != null ? list(innerType) : null;
        } else if (type instanceof NonNullType) {
            innerType = getTypeFromAST(schema, ((NonNullType) type).getType());
            return innerType != null ? nonNull(innerType) : null;
        }

        return schema.getType(((TypeName) type).getName());
    }
}
