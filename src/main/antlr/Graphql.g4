
//声明语法文件名称
grammar Graphql;

//导入的文件
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

//生成类的包路径
@header {
    package graphql.parser.antlr;
}

//document由各种定义组层
document : definition+;

//document组成元素：
definition:
operationDefinition | //操作定义：查询、更改、订阅
fragmentDefinition | //片段
typeSystemDefinition | //类型系统定义
typeSystemExtension //类型系统拓展
;







