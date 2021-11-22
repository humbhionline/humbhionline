package in.succinct.mandi.extensions;

import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.AfterModelSaveExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.SavedAddress;
import in.succinct.mandi.db.model.User;

public class AfterSaveOrderAddress extends AfterModelSaveExtension<OrderAddress> {
    static {
        registerExtension(new AfterSaveOrderAddress());
    }
    @Override
    public void afterSave(OrderAddress model) {
        if (Address.isAddressVoid(model)){
            return;
        }
        if (!ObjectUtil.equals(model.getAddressType(),OrderAddress.ADDRESS_TYPE_SHIP_TO)){
            return;
        }
        SavedAddress address = Database.getTable(SavedAddress.class).newRecord();
        Address.copy(model,address);
        User user = model.getCreatorUser().getRawRecord().getAsProxy(User.class);
        if (user != null){
            address.setUserId(user.getId());
            address.setFirstName(model.getFirstName());
            address.setLastName(model.getLastName());
            address.setLongName(model.getLongName());

            address = Database.getTable(SavedAddress.class).getRefreshed(address);
            if (address.getRawRecord().isNewRecord()){
                address.setNumDeliveries(new Bucket());
            }
            address.getNumDeliveries().increment();
            address.save();
        }

    }
}
