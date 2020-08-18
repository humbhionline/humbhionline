package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FacilitiesController extends ModelController<Facility> {
    public FacilitiesController(Path path) {
        super(path);
    }

    /* Is permission controlled */
    @RequireLogin
    public View verify(long id){
        Facility f = Database.getTable(Facility.class).get(id);
        f.verify();
        return show(f);
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        map.put(Inventory.class, ModelReflector.instance(Inventory.class).getFields());
        List<String> itemFields = ModelReflector.instance(Item.class).getUniqueFields();
        itemFields.add("ASSET_CODE_ID");
        map.put(Item.class, itemFields);

        List<String> skuFields = ModelReflector.instance(Sku.class).getUniqueFields();
        skuFields.add("MAX_RETAIL_PRICE");
        skuFields.add("TAX_RATE");

        map.put(Sku.class,skuFields);

        map.put(AssetCode.class, Arrays.asList("CODE","LONG_DESCRIPTION"));

        map.put(User.class,ModelReflector.instance(User.class).getUniqueFields());
        map.get(User.class).addAll(Arrays.asList("NAME_AS_IN_BANK_ACCOUNT","VIRTUAL_PAYMENT_ADDRESS"));

        return map;
    }
}
