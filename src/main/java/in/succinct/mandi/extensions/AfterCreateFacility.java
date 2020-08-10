package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.AfterModelCreateExtension;
import in.succinct.mandi.db.model.Facility;
import in.succinct.plugins.ecommerce.db.model.participation.PreferredCarrier;

public class AfterCreateFacility extends AfterModelCreateExtension<Facility> {
    @Override
    public void afterCreate(Facility model) {
        PreferredCarrier preferredCarrier = Database.getTable(PreferredCarrier.class).newRecord();
        preferredCarrier.setFacilityId(model.getId());
        preferredCarrier.setTaxesPaidBySender(true);
        preferredCarrier.setName(PreferredCarrier.HAND_DELIVERY);
        preferredCarrier.save();
        //Add Hand Delivery in all facilities.
    }
}
