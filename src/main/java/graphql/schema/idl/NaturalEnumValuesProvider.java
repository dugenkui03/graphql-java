package graphql.schema.idl;

import graphql.Assert;
import graphql.PublicApi;

/**
 * Simple EnumValuesProvided which maps the GraphQL Enum name to the Java Enum instance.
 *
 * 将graphql枚举值到java枚举值之间的映射。
 */
@PublicApi
public class NaturalEnumValuesProvider<T extends Enum<T>> implements EnumValuesProvider {

    // java枚举类
    private final Class<T> enumType;

    public NaturalEnumValuesProvider(Class<T> enumType) {
        Assert.assertNotNull(enumType, () -> "enumType can't be null");
        this.enumType = enumType;
    }

    // 返回对应名称的java枚举值
    @Override
    public T getValue(String name) {
        return Enum.valueOf(enumType, name);
    }
}
