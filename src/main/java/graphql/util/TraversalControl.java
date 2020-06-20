package graphql.util;

import graphql.masker.PublicApi;

/**
 * fixme 编辑控制值：继续、推出、中断
 * Special traversal control values
 */
@PublicApi
public enum TraversalControl {

    /**
     * When returned the traversal will continue as planned.
     */
    CONTINUE,
    /**
     * When returned from a Visitor's method, indicates exiting the traversal
     */
    QUIT,
    /**
     * When returned from a Visitor's method, indicates skipping traversal of a subtree.
     *
     * Not allowed to be returned from 'leave' or 'backRef' because it doesn't make sense.
     */
    ABORT
}
