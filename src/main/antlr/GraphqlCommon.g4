grammar GraphqlCommon;

// | 表示一种语法的多种规则
// 操作类型
operationType : SUBSCRIPTION | MUTATION | QUERY;

// 描述
description : stringValue;

enumValue : enumValueName ;


arrayValue: '[' value* ']';

arrayValueWithVariable: '[' valueWithVariable* ']';



objectValue: '{' objectField* '}';
objectValueWithVariable: '{' objectFieldWithVariable* '}';
objectField : name ':' value;
objectFieldWithVariable : name ':' valueWithVariable;


directives : directive+;

directive :'@' name arguments?;


arguments : '(' argument+ ')';

argument : name ':' valueWithVariable;

baseName: NAME | FRAGMENT | QUERY | MUTATION | SUBSCRIPTION | SCHEMA | SCALAR | TYPE | INTERFACE | IMPLEMENTS | ENUM | UNION | INPUT | EXTEND | DIRECTIVE;
fragmentName: baseName | BooleanValue | NullValue;
enumValueName: baseName | ON_KEYWORD;

name: baseName | BooleanValue | NullValue | ON_KEYWORD;

value :
stringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValue |
objectValue;


valueWithVariable :
variable |
stringValue |
IntValue |
FloatValue |
BooleanValue |
NullValue |
enumValue |
arrayValueWithVariable |
objectValueWithVariable;


variable : '$' name;

defaultValue : '=' value;

//规定语法定义符号的第一个字母小写，而词法定义符号的第一个字母大写
// 三引号 或者 双引号 中的内容
stringValue
 : TripleQuotedStringValue
 | StringValue
 ;
type : typeName | listType | nonNullType;

typeName : name;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';

//词法定义 第一个关键字大写
//这些 词法定义 被称为Token
BooleanValue: 'true' | 'false';

NullValue: 'null';

FRAGMENT: 'fragment';
QUERY: 'query';
MUTATION: 'mutation';
SUBSCRIPTION: 'subscription';
SCHEMA: 'schema';
SCALAR: 'scalar';
TYPE: 'type';
INTERFACE: 'interface';
IMPLEMENTS: 'implements';
ENUM: 'enum';
UNION: 'union';
INPUT: 'input';
EXTEND: 'extend';
DIRECTIVE: 'directive';
ON_KEYWORD: 'on';
NAME: [_A-Za-z][_0-9A-Za-z]*;


IntValue : Sign? IntegerPart;

FloatValue : Sign? IntegerPart ('.' Digit+)? ExponentPart?;

Sign : '-';

IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

NonZeroDigit: '1'.. '9';

ExponentPart : ('e'|'E') ('+'|'-')? Digit+;

Digit : '0'..'9';


StringValue
// (...)* 匹配0个或多个 ...
 : '"' ( ~["\\\n\r\u2028\u2029] | EscapedChar )* '"'
 ;

// 符号写在单引号内
TripleQuotedStringValue
 : '"""' TripleQuotedStringPart? '"""'
 ;


// Fragments never become a token of their own: they are only used inside other lexer rules
// "Fragments永远不会成为他们自己的token：他们只在其他词法分析器规则中使用"
//词法分析器规则前面的 fragment 指示该规则仅用作另一词法分析器规则的一部分
//(...)+ 匹配一个或多个 ...
fragment TripleQuotedStringPart : ( EscapedTripleQuote | ExtendedSourceCharacter )+?;

// 转义 """
fragment EscapedTripleQuote : '\\"""';

// 当前的graphql规范并未cover此内容：graphql-java的实现允许 unicode 字符 ： 制表符、换行符、空格
// this is currently not covered by the spec because we allow all unicode chars
// u0009 = \t Horizontal tab
// u000a = \n line feed
// u000d = \r carriage return
// u0020 = space
fragment ExtendedSourceCharacter :[\u0009\u000A\u000D\u0020-\u{10FFFF}];

// 比 ExtendedSourceCharacter 少了换行符
fragment ExtendedSourceCharacterWithoutLineFeed :[\u0009\u0020-\u{10FFFF}];

// this is the spec definition
// fragment SourceCharacter :[\u0009\u000A\u000D\u0020-\uFFFF];


// "--hiden  定义需要隐藏的文本，指向channel(HIDDEN)就会隐藏。这里的channel可以自定义，到时在后台获取不同的channel的数据进行不同的处理"
// 参考 GraphqlAntlrToLanguage 中对 #注释 的处理：CHANNEL_COMMENTS
Comment: '#' ExtendedSourceCharacterWithoutLineFeed* -> channel(2);

fragment EscapedChar :   '\\' (["\\/bfnrt] | Unicode) ;
fragment Unicode : 'u' Hex Hex Hex Hex ;
fragment Hex : [0-9a-fA-F] ;

LF: [\n] -> channel(3);
CR: [\r] -> channel(3);
LineTerminator: [\u2028\u2029] -> channel(3);

Space : [\u0020] -> channel(3);
Tab : [\u0009] -> channel(3);
Comma : ',' -> channel(3);
UnicodeBOM : [\ufeff] -> channel(3);
