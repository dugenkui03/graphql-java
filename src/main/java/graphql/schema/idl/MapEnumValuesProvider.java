package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;

import java.util.Map;

/**
 * 将graphql枚举类型的字段和具体的值绑定，"具体的值"指 {@link graphql.schema.GraphQLEnumValueDefinition} 中的 value 字段
 */
@PublicApi
public class MapEnumValuesProvider implements EnumValuesProvider {


    private final Map<String, Object> values;

    public MapEnumValuesProvider(Map<String, Object> values) {
        Assert.assertNotNull(values, () -> "values can't be null");
        this.values = values;
    }

    @Override
    public Object getValue(String name) {
        return values.get(name);
    }
}
