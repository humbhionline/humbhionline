package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.AfterModelSaveExtension;
import com.venky.swf.plugins.background.core.TaskManager;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Item;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

public class AfterSaveFacility extends AfterModelSaveExtension<Facility> {
    static {
        registerExtension(new AfterSaveFacility());
    }
    @Override
    public void afterSave(Facility model) {
        /*
        PreferredCarrier preferredCarrier = Database.getTable(PreferredCarrier.class).newRecord();
        preferredCarrier.setFacilityId(model.getId());
        preferredCarrier.setTaxesPaidBySender(true);
        preferredCarrier.setName("HAND_DELIVERY");
        preferredCarrier.save();
        //Add Hand Delivery in all facilities.
        */
        Inventory inventory = model.getDeliveryRule(true);
        if (inventory == null){
            inventory = model.getDeliveryRule(false);
        }
        if (model.isDeliveryProvided()){
            if (inventory == null){
                inventory = Database.getTable(Inventory.class).newRecord();
                inventory.setInfinite(false);
                Sku deliverySku = null;
                for (Sku sku : AssetCode.getDeliverySkus()){
                    if (sku.getItem().getAssetCode().getGstPct() > 0){
                        if (!sku.getItem().getRawRecord().getAsProxy(Item.class).isItemRestrictedToSingleSeller()) {
                            deliverySku = sku;
                            break;
                        }
                    }
                }
                inventory.setFacilityId(model.getId());
                inventory.setSkuId(deliverySku.getId());
                inventory.setTags("Delivery");
                inventory.setCompanyId(deliverySku.getCompanyId());
            }

            double cf = UnitOfMeasureConversionTable.convert(1, UnitOfMeasure.MEASURES_PACKAGING,UnitOfMeasure.KILOMETERS,
                    inventory.getSku().getPackagingUOM().getName());

            inventory.setMaxRetailPrice(model.getChargesPerKm()*cf);
            inventory.setSellingPrice(model.getChargesPerKm()*cf);
            inventory.setEnabled(false);
            inventory.save();

        }else if (inventory != null ){
            inventory.destroy();
        }
        
    }
}
