package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

import java.util.List;
import java.util.stream.Collectors;

public class BeforeSaveInventory extends BeforeModelSaveExtension<Inventory> {
    static {
        registerExtension(new BeforeSaveInventory());
    }
    @Override
    public void beforeSave(Inventory inventory) {
        Sku sku = inventory.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        if (inventory.getRawRecord().isNewRecord()){
            inventory.setInfinite(false);
        }
        if (item.isItemRestrictedToSingleSeller()){
            List<Sku> skus = item.getSkus();
            List<Long> skuIds = skus.stream().map(s->s.getId()).collect(Collectors.toList());
            ModelReflector<Inventory> ref = ModelReflector.instance(Inventory.class);

            List<Inventory> inventoryList = new Select().from(Inventory.class).
                    where(new Expression(ref.getPool(), Conjunction.AND).
                            add(new Expression(ref.getPool(),"SKU_ID" , Operator.IN, skuIds.toArray())).
                            add(new Expression(ref.getPool(), "FACILITY_ID", Operator.NE, inventory.getFacilityId()))).
                    execute(1);
            if (!inventoryList.isEmpty()){
                throw new RuntimeException("Sku can be sold only from " + inventoryList.get(0).getFacility().getName() );
            }
        }

    }
}
