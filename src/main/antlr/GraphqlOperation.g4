grammar GraphqlOperation;
import GraphqlCommon;

//操作定义：选择集
operationDefinition:
selectionSet |
operationType  name? variableDefinitions? directives? selectionSet;


// 查询变量：    (变量定义,,,)
variableDefinitions : '(' variableDefinition+ ')';

//变量定义： 变量值、变量类型和变量默认值
variableDefinition : variable ':' type defaultValue?;


selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? name arguments? directives? selectionSet?;

alias : name ':';



fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' typeCondition? directives? selectionSet;

fragmentDefinition : FRAGMENT fragmentName typeCondition directives? selectionSet;


typeCondition : ON_KEYWORD typeName;
