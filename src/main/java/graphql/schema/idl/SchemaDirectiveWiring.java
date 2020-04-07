package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLUnionType;

/**
 * 该类基于定义在SDL元素上的指令、增强该元素的运行时行为。
 *
 * A SchemaDirectiveWiring is responsible for enhancing a runtime element based on directives placed on that
 * element in the Schema Definition Language (SDL).
 * <p>
 *    他可以增强graphql元素运行时的行为，例如通过改变DataFetcher；
 * It can enhance the graphql runtime element and add new behaviour for example by changing
 * the fields {@link graphql.schema.DataFetcher}
 * <p>
 *     根据注册，顺序调用SchemaDirectiveWiring对象
 * The SchemaDirectiveWiring objects are called in a specific order based on registration:
 * <ol>
 *
 * <li>{@link graphql.schema.idl.RuntimeWiring.Builder#directive(String, SchemaDirectiveWiring)} which work against a specific named directive are called first</li>
 *
 * <li>{@link graphql.schema.idl.RuntimeWiring.Builder#directiveWiring(SchemaDirectiveWiring)} which work against all directives are called next</li>
 *
 * <li>{@link graphql.schema.idl.WiringFactory#providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment)} which work against all directives are called last</li>
 * </ol>
 * <p>
 */
@PublicApi
public interface SchemaDirectiveWiring {

    /**
     * fixme 当遇到一个对象的时候调用此方法，使得指令可以修改DSL元素的内容和行为。
     *
     * This is called when an object is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     * <p></p>
     * <p>
     *     onArgument和onField的回调将事先调用此方法
     * The {@link #onArgument(SchemaDirectiveWiringEnvironment)} and {@link #onField(SchemaDirectiveWiringEnvironment)} callbacks will have been
     * invoked for this element beforehand
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one：非空元素
     */
    default GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
        return environment.getElement();
    }

    /**
     * fixme 当遇到field的时候调用此方法。
     *
     * This is called when a field is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     * <p>
     * The {@link #onArgument(SchemaDirectiveWiringEnvironment)} callbacks will have been
     * invoked for this element beforehand
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        return environment.getElement();
    }

    /**
     * fixme 当遇到参数时候调用此方法。
     *
     * This is called when an argument is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLArgument onArgument(SchemaDirectiveWiringEnvironment<GraphQLArgument> environment) {
        return environment.getElement();
    }

    /**
     * fixme 遇到接口的时候调用此方法。
     *
     * This is called when an interface is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     * <p>
     * The {@link #onArgument(SchemaDirectiveWiringEnvironment)} and {@link #onField(SchemaDirectiveWiringEnvironment)} callbacks will have been
     * invoked for this element beforehand
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLInterfaceType onInterface(SchemaDirectiveWiringEnvironment<GraphQLInterfaceType> environment) {
        return environment.getElement();
    }

    /**
     * fixme 遇到union的时候调用此方法。
     *
     * This is called when a union is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLUnionType onUnion(SchemaDirectiveWiringEnvironment<GraphQLUnionType> environment) {
        return environment.getElement();
    }

    /**
     * fixme 枚举。
     *
     * This is called when an enum is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     * <p>
     * The {@link #onEnumValue(SchemaDirectiveWiringEnvironment)} callbacks will have been invoked for this element beforehand
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLEnumType onEnum(SchemaDirectiveWiringEnvironment<GraphQLEnumType> environment) {
        return environment.getElement();
    }

    /**
     * fixme 当遇到枚举值的时候调用此方法。
     *
     * This is called when an enum value is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL element
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLEnumValueDefinition onEnumValue(SchemaDirectiveWiringEnvironment<GraphQLEnumValueDefinition> environment) {
        return environment.getElement();
    }

    /**
     * This is called when a custom scalar is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL  element
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLScalarType onScalar(SchemaDirectiveWiringEnvironment<GraphQLScalarType> environment) {
        return environment.getElement();
    }

    /**
     * This is called when an input object is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL  element
     * <p>
     * The {@link #onInputObjectField(SchemaDirectiveWiringEnvironment)}callbacks will have been invoked for this element beforehand
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
        return environment.getElement();
    }

    /**
     * This is called when an input object field is encountered, which gives the schema directive a chance to modify the shape and behaviour
     * of that DSL  element
     *
     * @param environment the wiring element
     *
     * @return a non null element based on the original one
     */
    default GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
        return environment.getElement();
    }
}
