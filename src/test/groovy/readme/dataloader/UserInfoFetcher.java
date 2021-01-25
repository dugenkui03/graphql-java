package readme.dataloader;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/**
 * @Date 2021/1/25
 **/
public class UserInfoFetcher implements DataFetcher {

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        List<Integer> userIds = environment.getArgument("userIds");
        return invokeRpc(userIds);
    }

    /**
     * todo
     *  1. 模拟调用rpc：应该是根据key找到对应的数据源；
     *  2. xListFetcher也可能有n+1问题，但是在批量加载可能没有啥意义。
     */
    List<Map> invokeRpc(List<Integer> userIds) {
        List<Map> result = new LinkedList<>();
        for (Integer userId : userIds) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", userId);
            userInfo.put("name", "name: " + userId);
            userInfo.put("mostLikeItem", userId * 10);
            result.add(userInfo);
        }
        return result;
    }
}
