package graphql.schema.idl.errors;

import graphql.language.TypeDefinition;

import static java.lang.String.format;

/**
 * https://spec.graphql.org/June2018/#sec-Schema
 *
 * 1. All types within a GraphQL schema must have unique names. No two provided types may have the same name.
 * No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).
 * fixme schame中所有的类型名称必须是全局唯一的，包括不能和标量、Introspection类型冲突。
 */
public class TypeRedefinitionError extends BaseError {

    public TypeRedefinitionError(TypeDefinition newEntry, TypeDefinition oldEntry) {
        super(oldEntry,
                format("'%s' type %s tried to redefine existing '%s' type %s",
                        newEntry.getName(), BaseError.lineCol(newEntry), oldEntry.getName(), BaseError.lineCol(oldEntry)
                ));
    }
}
