package graphql.language;


import graphql.PublicApi;

//？？接口、union、对象、输入类型、枚举、标量、指令和片段等的定义？？
@PublicApi
public interface Definition<T extends Definition> extends Node<T> {

}
