// 比较好的一篇博客 https://liangshuang.name/2017/08/20/antlr/
// 比较好的一篇博客*2 https://zhmin.github.io/2019/04/26/antlr4-tutorial/

//grammar 的名字，名字需要与文件名对应
grammar Graphql; //Lexer 词法

//
import GraphqlSDL, GraphqlOperation, GraphqlCommon;

@header {
    package graphql.parser.antlr;
}


//整个文档的子节点就是"定义"，定义的子节点还是各种操作、片段和类型系统定义
document : definition+;

//operationDefinition生成的上下文对象为operationDefinition+"Context"、使用"Context"后缀
//但是也可以使用  # 指定名称 ，参见http://icejoywoo.github.io/2019/01/16/intro-to-antlr4.html

// | 表示一种语法的多种规则
definition:
operationDefinition |
fragmentDefinition |
typeSystemDefinition |
typeSystemExtension
;







