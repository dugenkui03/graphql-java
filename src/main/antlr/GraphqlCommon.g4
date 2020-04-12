grammar GraphqlCommon;

operationType : SUBSCRIPTION | MUTATION | QUERY;

description : stringValue;

enumValue : enumValueName ;


arrayValue: '[' value* ']';

arrayValueWithVariable: '[' valueWithVariable* ']';



objectValue: '{' objectField* '}';
objectValueWithVariable: '{' objectFieldWithVariable* '}';
objectField : name ':' value;
objectFieldWithVariable : name ':' valueWithVariable;


//指令集合
directives : directive+;

//指令定义：https://spec.graphql.org/June2018/#sec-Type-System.Directives；?在正则里边表示0次或者一次
directive :'@' name arguments?;


arguments : '(' argument+ ')';

argument : name ':' valueWithVariable;

baseName: NAME | FRAGMENT | QUERY | MUTATION | SUBSCRIPTION | SCHEMA | SCALAR | TYPE | INTERFACE | IMPLEMENTS | ENUM | UNION | INPUT | EXTEND | DIRECTIVE;
fragmentName: baseName | BooleanValue | NullValue;
enumValueName: baseName | ON_KEYWORD;


// 名称：基本名称或者 布尔值|空值|on关键字
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


//变量定义： $和名称
variable : '$' name;

defaultValue : '=' value;

stringValue
 : TripleQuotedStringValue
 | StringValue
 ;
type : typeName | listType | nonNullType;

typeName : name;
listType : '[' type ']';
nonNullType: typeName '!' | listType '!';


//最基本的词法元素：布尔值、空值、片段、query|更新|订阅、schema、标量、类型、接口、实现、枚举、输入、拓展、指令、on关键字、名称正则表达式
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


//整数定义：optional的符号和整数
IntValue : Sign? IntegerPart;

//浮点数定义
FloatValue : Sign? IntegerPart ('.' Digit+)? ExponentPart?;

Sign : '-';


//整数定义：0；1到9的数；超过9的数字
IntegerPart : '0' | NonZeroDigit | NonZeroDigit Digit+;

//1到9
NonZeroDigit: '1'.. '9';

ExponentPart : ('e'|'E') ('+'|'-')? Digit+;

//0到9
Digit : '0'..'9';


StringValue
 : '"' ( ~["\\\n\r\u2028\u2029] | EscapedChar )* '"'
 ;

TripleQuotedStringValue
 : '"""' TripleQuotedStringPart? '"""'
 ;


// Fragments never become a token of their own: they are only used inside other lexer rules
fragment TripleQuotedStringPart : ( EscapedTripleQuote | ExtendedSourceCharacter )+?;
fragment EscapedTripleQuote : '\\"""';

// this is currently not covered by the spec because we allow all unicode chars
// u0009 = \t Horizontal tab
// u000a = \n line feed
// u000d = \r carriage return
// u0020 = space
fragment ExtendedSourceCharacter :[\u0009\u000A\u000D\u0020-\u{10FFFF}];

fragment ExtendedSourceCharacterWithoutLineFeed :[\u0009\u0020-\u{10FFFF}];

// this is the spec definition
// fragment SourceCharacter :[\u0009\u000A\u000D\u0020-\uFFFF];


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
