package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Select.ResultFilter;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InventoriesController extends ModelController<Inventory> {
    public InventoriesController(Path path) {
        super(path);
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE"));
        return map;
    }

    @Override
    protected ResultFilter<Inventory> getFilter() {

        return record ->
                ((record.getFacility().getRawRecord().getAsProxy(Facility.class).getDistance() < 20 &&
                        record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0)
                        || record.getSku().getItem().getRawRecord().getAsProxy(Item.class).isHumBhiOnlineSubscriptionItem())
                        && (record.isInfinite() || record.getQuantity() > 0)
                        && super.getFilter().pass(record);
    }
}