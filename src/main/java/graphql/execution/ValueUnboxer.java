package graphql.execution;

import graphql.PublicSpi;

//todo 可以将所有fetcher结果 to Map的逻辑放到这儿呀。
@PublicSpi
public interface ValueUnboxer {

    ValueUnboxer DEFAULT = new DefaultValueUnboxer();

    /**
     * Unboxes 'object' if it is boxed in an {@link java.util.Optional } like
     * type that this unboxer can handle. Otherwise returns its input
     * unmodified
     *
     * @param object to unbox
     *
     * @return unboxed object, or original if cannot unbox
     */
    Object unbox(final Object object);
}