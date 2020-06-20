package graphql.schema;

import graphql.masker.PublicApi;

import java.util.Stack;

import static graphql.util.Assert.assertNotNull;
import static graphql.util.Assert.assertShouldNeverHappen;

/**
 * A utility class that helps work with {@link graphql.schema.GraphQLType}s
 */
@PublicApi
public class GraphQLTypeUtil {

    /**
     * 以SDL打印类型名称
     * This will return the type in graphql SDL format, eg [typeName!]!
     *
     * @param type the type in play
     *
     * @return the type in graphql SDL format, eg [typeName!]!
     */
    public static String simplePrint(GraphQLType type) {
        StringBuilder sb = new StringBuilder();
        if (isNonNull(type)) {
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("!");
        } else if (isList(type)) {
            sb.append("[");
            sb.append(simplePrint(unwrapOne(type)));
            sb.append("]");
        } else {
            sb.append(((GraphQLNamedType) type).getName());
        }
        return sb.toString();
    }

    //以SDL格式打印schema元素名称
    public static String simplePrint(GraphQLSchemaElement schemaElement) {
        if (schemaElement instanceof GraphQLType) {
            return simplePrint((GraphQLType) schemaElement);
        }
        if (schemaElement instanceof GraphQLNamedSchemaElement) {
            return ((GraphQLNamedSchemaElement) schemaElement).getName();
        }
        // a schema element is either a GraphQLType or a GraphQLNamedSchemaElement
        return assertShouldNeverHappen("unexpected schema element: " + schemaElement);
    }

    /**
     * Returns true if the given type is a non null type
     *
     * @param type the type to check
     *
     * @return true if the given type is a non null type
     */
    public static boolean isNonNull(GraphQLType type) {
        return type instanceof GraphQLNonNull;
    }

    /**
     * Returns true if the given type is a nullable type
     *
     * @param type the type to check
     *
     * @return true if the given type is a nullable type
     */
    public static boolean isNullable(GraphQLType type) {
        return !isNonNull(type);
    }

    /**
     * Returns true if the given type is a list type
     *
     * @param type the type to check
     *
     * @return true if the given type is a list type
     */
    public static boolean isList(GraphQLType type) {
        return type instanceof GraphQLList;
    }

    /**
     * 是否被list或者非空包装
     * Returns true if the given type is a non null or list type, that is a wrapped type
     *
     * @return true if the given type is a non null or list type
     */
    public static boolean isWrapped(GraphQLType type) {
        return isList(type) || isNonNull(type);
    }

    /**
     * @return true if the given type is NOT a non null or list type
     *         Returns true if the given type is NOT a non null or list type
     */
    public static boolean isNotWrapped(GraphQLType type) {
        return !isWrapped(type);
    }


    //是否是标量
    public static boolean isScalar(GraphQLType type) {
        return type instanceof GraphQLScalarType;
    }

    //@return 是否是枚举
    public static boolean isEnum(GraphQLType type) {
        return type instanceof GraphQLEnumType;
    }

    /**
     * fixme
     *      给定的类型是否是叶子节点类型： 拆箱后是标量或者枚举；
     *      叶子节点不能包含任何字段。
     * Returns true if the given type is a leaf type, that it cant contain any more fields
     *
     * @param type the type to check
     *
     * @return true if the given type is a leaf type
     */
    public static boolean isLeaf(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = unwrapAll(type);
        return
                //子类 instanceof 父类 == true
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType;
    }

    /**
     * 是否是输入类型的：标量、枚举或者输入类型
     *
     * @param type the type to check
     *
     * @return true if the given type is an input type
     */
    public static boolean isInput(GraphQLType type) {
        GraphQLUnmodifiedType unmodifiedType = unwrapAll(type);
        return
                unmodifiedType instanceof GraphQLScalarType
                        || unmodifiedType instanceof GraphQLEnumType
                        || unmodifiedType instanceof GraphQLInputObjectType;
    }

    /**
     * 去掉一层包装：非空或者List元素
     * Unwraps one layer of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to unwrapOne
     *
     * @return the unwrapped type or the same type again if its not wrapped
     */
    public static GraphQLType unwrapOne(GraphQLType type) {
        if (isNonNull(type)) {
            return ((GraphQLNonNull) type).getWrappedType();
        } else if (isList(type)) {
            return ((GraphQLList) type).getWrappedType();
        }
        return type;
    }

    /**
     * 去掉多层的包装
     * Unwraps all layers of the type or just returns the type again if its not a wrapped type
     *
     * @param type the type to unwrapOne
     *
     * @return the underlying type
     */
    public static GraphQLUnmodifiedType unwrapAll(GraphQLType type) {
        while (true) {
            if (isNotWrapped(type)) {
                return (GraphQLUnmodifiedType) type;
            }
            type = unwrapOne(type);
        }
    }

    //去掉一层非空包装-非空包装只能有一层、否则校验不通过
    public static GraphQLType unwrapNonNull(GraphQLType type) {
        while (isNonNull(type)) {
            type = unwrapOne(type);
        }
        return type;
    }

    /**
     * 获取拆分的多层包装
     * graphql types can be wrapped in {@link GraphQLNonNull} and {@link GraphQLList} type wrappers
     * so this method will unwrap the type down to the raw unwrapped type and return that wrapping
     * as a stack, with the top of the stack being the raw underling type.
     *
     * @param type the type to unwrap
     *
     * @return a stack of the type wrapping which will be at least 1 later deep
     */
    public static Stack<GraphQLType> unwrapType(GraphQLType type) {
        type = assertNotNull(type);
        Stack<GraphQLType> decoration = new Stack<>();
        while (true) {
            decoration.push(type);
            if (isNotWrapped(type)) {
                break;
            }
            type = unwrapOne(type);
        }
        return decoration;
    }
}
