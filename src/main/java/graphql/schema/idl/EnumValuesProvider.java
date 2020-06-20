package graphql.schema.idl;//idl 接口定义语言

import graphql.masker.PublicSpi;

/**
 * fixme 对每一个graphql枚举提供java运行时值，使用IDL驱动schema创建。
 *
 * Provides the Java runtime value for each graphql Enum value. Used for IDL driven schema creation.
 * <p>
 *
 * fixme 可认为枚举值是静态的，创建schema时会调用它、执行查询时不会使用。
 * Enum values are considered static: This is called when a schema is created. It is not used when a query is executed.
 */
@PublicSpi
public interface EnumValuesProvider {

    /**
     * @param name an Enum value
     *
     * @return not null
     */
    Object getValue(String name);

}
