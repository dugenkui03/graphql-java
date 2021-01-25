package readme.dataloader;

import java.util.Map;

import org.dataloader.DataLoader;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/**
 **/
public class ItemInfoFetcher implements DataFetcher {

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Integer itemId = (Integer) ((Map) environment.getSource()).get("mostLikeItem");

        // fixme 实现的基础应该是有 getItemListByIds
        //       todo itemIdLoader应该也是根据字段名称定位到的；
        DataLoader<Integer, Object> dataLoader = environment.getDataLoader("itemIdLoader");

        return dataLoader.load(itemId);
    }
}
