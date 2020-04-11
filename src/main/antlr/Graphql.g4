
//声明语法文件名称
grammar Graphql;

//导入的文件
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

//生成类的包路径
@header {
    package graphql.parser.antlr;
}

//todo
document : definition+;

// 生命一个名称为definition的规则，该规则可以匹配操作定义、片段定义、类型系统定义和类型系统拓展，四个可匹配的分支
definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition |
typeSystemExtension
;







