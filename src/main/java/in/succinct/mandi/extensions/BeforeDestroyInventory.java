package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelDestroyExtension;
import com.venky.swf.exceptions.AccessDeniedException;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;

public class BeforeDestroyInventory extends BeforeModelDestroyExtension<Inventory> {
    static {
        registerExtension(new BeforeDestroyInventory());
    }
    @Override
    public void beforeDestroy(Inventory model) {
        User user = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (!user.getOperatingFacilityIds().contains(model.getFacilityId()) && !user.isStaff() && !user.isAdmin()){
            throw  new AccessDeniedException("Only the facility owner can delete the inventory in the facility!");
        }
        if (AssetCode.getDeliverySkuIds().contains(model.getSkuId())){
            Facility facility = model.getFacility().getRawRecord().getAsProxy(Facility.class);
            if (facility.isPublished() && facility.isDeliveryProvided()){
                throw new RuntimeException("Cannot delete this service from published facilities which declare to provide this service.");
            }
        }
    }
}
