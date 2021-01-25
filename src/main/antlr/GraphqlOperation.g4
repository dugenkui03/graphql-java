grammar GraphqlOperation;
import GraphqlCommon;

operationDefinition:
selectionSet |
//名称.可选；变量定义.可选；指令.可选；selectionSet
operationType  name? variableDefinitions? directives? selectionSet;

//括号内多个 变量定义
variableDefinitions : '(' variableDefinition+ ')';

//variable : $name；
//type : typeName | listType | nonNullType;
variableDefinition : variable ':' type defaultValue? directives?;


selectionSet :  '{' selection+ '}';

selection :
field |
fragmentSpread |
inlineFragment;

field : alias? name arguments? directives? selectionSet?;

alias : name ':';



fragmentSpread : '...' fragmentName directives?;

inlineFragment : '...' typeCondition? directives? selectionSet;

//片段上不定义参数
fragmentDefinition : FRAGMENT fragmentName typeCondition directives? selectionSet;


typeCondition : ON_KEYWORD typeName;
