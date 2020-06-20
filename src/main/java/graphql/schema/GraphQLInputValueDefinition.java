package graphql.schema;

import graphql.masker.PublicApi;

/**
 * Named schema elements that contain input type information.
 *
 *
 * @see graphql.schema.GraphQLInputType
 * @see graphql.schema.GraphQLInputObjectField
 * @see graphql.schema.GraphQLArgument
 */
@PublicApi
public interface GraphQLInputValueDefinition extends GraphQLDirectiveContainer {

    <T extends GraphQLInputType> T getType();
}
