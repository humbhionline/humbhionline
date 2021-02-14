package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelDestroyExtension;
import com.venky.swf.exceptions.AccessDeniedException;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.User;

public class BeforeDestroyInventory extends BeforeModelDestroyExtension<Inventory> {
    static {
        registerExtension(new BeforeDestroyInventory());
    }
    @Override
    public void beforeDestroy(Inventory model) {
        User user = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (!user.getOperatingFacilityIds().contains(model.getFacilityId()) && !user.isStaff()){
            throw  new AccessDeniedException("Only the facility owner can delete the inventory in the facility!");
        }
    }
}
