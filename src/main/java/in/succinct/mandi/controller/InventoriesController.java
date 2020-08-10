package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import in.succinct.mandi.db.model.Facility;
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

}