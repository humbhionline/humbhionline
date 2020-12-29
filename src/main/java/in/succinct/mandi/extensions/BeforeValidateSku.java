package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

public class BeforeValidateSku  extends BeforeModelValidateExtension<Sku> {
    static {
        registerExtension(new BeforeValidateSku());
    }
    @Override
    public void beforeValidate(Sku model) {
        Item item  = model.getItem();
        if (item == null){
            return;
        }
        if (item.getAssetCodeId() != null){
            model.setTaxRate(item.getAssetCode().getGstPct());
        }
        UnitOfMeasure uom= model.getPackagingUOM();

        if (model.getReflector().isVoid(model.getName()) && uom != null){
            model.setName(item.getName() + "-" + uom.getName());
        }
    }
}
