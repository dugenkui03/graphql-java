package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Value;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInputValueDefinition;

/**
 * 参数输入类型对应的树形结构
 * This describes the tree structure that forms from a argument input type,
 * especially with `input ComplexType { ....}` types that might in turn contain other complex
 * types and hence form a tree of values.
 */
@PublicApi
public interface QueryVisitorFieldArgumentInputValue {

    //父亲
    QueryVisitorFieldArgumentInputValue getParent();

    //对应的输入类型定义
    GraphQLInputValueDefinition getInputValueDefinition();

    //InputValueDefinition的名称
    String getName();

    //InputValueDefinition的类型
    GraphQLInputType getInputType();

    Value getValue();
}
