## 一、框架待做 
1. 有时候，需要根据一个字段的输入，作为另外一个字段的参数
所以是否应该允许修改参数、使用instrumentArguemnt方法支持； 首先查询参数信息的地方就得收口
2. 间接引用自己校验
 directive @invalidExample(arg: String @invalidExample) on ARGUMENT_DEFINITION；

