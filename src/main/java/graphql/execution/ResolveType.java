package graphql.execution;

import graphql.Internal;
import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;

import java.util.Map;

@Internal
public class ResolveType {

    /**
     * 对interface、union进行处理，type则直接返回
     */
    public GraphQLObjectType resolveType(ExecutionContext executionContext, //执行上下文
                                         MergedField field, //当前字段
                                         Object source, //结果
                                         Map<String, Object> arguments, //该字段上的参数
                                         GraphQLType fieldType) { //该字段的类型

        GraphQLObjectType resolvedType;

        //如果是接口类型
        if (fieldType instanceof GraphQLInterfaceType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(arguments)
                    .context(executionContext.getContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);
        }
        //如果是union类型
        else if (fieldType instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(arguments)
                    .context(executionContext.getContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        }
        //如果是Object类型
        else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        return resolvedType;
    }

    public GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema(), params.getContext());
        GraphQLInterfaceType abstractType = params.getGraphQLInterfaceType();
        TypeResolver typeResolver = params.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        GraphQLObjectType result = typeResolver.getType(env);
        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!params.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

    public GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema(), params.getContext());
        GraphQLUnionType abstractType = params.getGraphQLUnionType();
        TypeResolver typeResolver = params.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        GraphQLObjectType result = typeResolver.getType(env);
        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!params.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

}
