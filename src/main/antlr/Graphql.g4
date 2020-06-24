grammar Graphql;
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}


//整个文档的子节点就是"定义"，定义的子节点还是各种操作、片段和类型系统定义
document : definition+;

definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition |
typeSystemExtension
;







