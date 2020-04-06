package graphql.execution.directives;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.schema.GraphQLDirective;

import java.util.List;
import java.util.Map;

/**
 * 允许您直接访问MergedField上关联的指令，但是不包括副字段或者片段包含的指令。
 *
 * This gives you access to the immediate directives on a {@link graphql.execution.MergedField}.  This does not include directives on parent
 * fields or fragment containers.
 * <p>
 *
 * 因为MergedField可以拥有多个字段，因此每个字段实例上的指令"there is more than one directive named "foo" on the merged field"，
 * 如何使用取决于你的代码。
 *
 * Because a {@link graphql.execution.MergedField} can actually have multiple fields and hence
 * directives on each field instance its possible that there is more than one directive named "foo"
 * on the merged field.  How you decide which one to use is up to your code.
 * <p>
 * NOTE: A future version of the interface will try to add access to the inherited directives from
 * parent fields and fragments.  This proved to be a non trivial problem and hence we decide
 * to give access to immediate field directives and provide this holder interface so we can
 * add the other directives in the future
 *
 * @see graphql.execution.MergedField
 */
@PublicApi
public interface QueryDirectives {

    /**
     * 返回与merged字段直接关联的指令map
     * This will return a map of the directives that are immediately on a merged field
     *
     * @return a map of all the directives immediately(直接的) on this merged field
     */
    Map<String, List<GraphQLDirective>> getImmediateDirectivesByName();


    /**
     * 返回与merged字段直接关联的指令List，其中key是指令名称
     *
     * This will return a list of the named directives that are immediately on this merged field.
     *
     * Read above for why this is a list of directives and not just one
     *
     * @param directiveName the named directive
     *
     * @return a list of the named directives that are immediately on this merged field
     */
    List<GraphQLDirective> getImmediateDirective(String directiveName);

    /**
     * 按照查询字段分组
     *
     * This will return a map of the {@link graphql.language.Field}s inside a {@link graphql.execution.MergedField}
     * and the immediate directives that are on each specific field
     *
     * @return a map of all directives on each field inside this
     */
    Map<Field, List<GraphQLDirective>> getImmediateDirectivesByField();
}
