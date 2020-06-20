package graphql.analysis;

import graphql.masker.PublicApi;
import graphql.analysis.environment.QueryVisitorFieldEnvironment;
import graphql.analysis.environment.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.environment.QueryVisitorInlineFragmentEnvironment;

//查询访问者，空操作，方便使用
@PublicApi
public class QueryVisitorStub implements QueryVisitor {


    @Override
    public void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {

    }

    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}
