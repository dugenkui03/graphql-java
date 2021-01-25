package readme.dataloader;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.dataloader.BatchLoader;

/**
 * todo 抽象+getName / getKey
 *
 * @Date 2021/1/25
 **/
public class ItemLoaderFunc implements BatchLoader<Integer,Map> {

    @Override
    public CompletionStage<List<Map>> load(List<Integer> itemIds) {
        List<Map> result = new LinkedList<>();
        for (Integer userId : itemIds) {
            Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("id", userId);
            itemInfo.put("itemName", "itemName: " + userId);
            result.add(itemInfo);
        }

        return CompletableFuture.completedFuture(result);
    }
}
