package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLOutputType;

import java.util.List;

/**
 * ？？字段绑定环境？？
 */
@PublicApi
public class FieldWiringEnvironment extends WiringEnvironment {

    // 字段定义
    private final FieldDefinition fieldDefinition;
    // 字段所在类型
    private final TypeDefinition parentType;
    // 字段的类型
    private final GraphQLOutputType fieldType;
    // 字段上定义的指令
    private final List<GraphQLDirective> directives;

    //  父类字段
    //  private final TypeDefinitionRegistry registry;

    FieldWiringEnvironment(TypeDefinitionRegistry registry,
                           TypeDefinition parentType,
                           FieldDefinition fieldDefinition,
                           GraphQLOutputType fieldType,
                           List<GraphQLDirective> directives) {
        super(registry);
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.fieldType = fieldType;
        this.directives = directives;
    }

    public FieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public TypeDefinition getParentType() {
        return parentType;
    }

    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    public List<GraphQLDirective> getDirectives() {
        // todo 应该是不需要返回拷贝的
        return directives;
    }
}