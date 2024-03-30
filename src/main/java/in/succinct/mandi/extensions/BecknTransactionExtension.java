package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.ModelOperationExtension;
import com.venky.swf.plugins.collab.db.model.user.Phone;

import in.succinct.beckn.Billing;
import in.succinct.beckn.Order;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.beckn.BecknTransaction;

public class BecknTransactionExtension extends ModelOperationExtension<BecknTransaction> {
    static {
        registerExtension(new BecknTransactionExtension());
    }

    @Override
    protected void beforeValidate(BecknTransaction instance) {
        if (instance.getBuyerId() == null && instance.getOrderJson() != null) {
            Order order = new Order(instance.getOrderJson());
            Billing billing = order.getBilling();
            String phoneNumber = billing == null ? null : billing.getPhone();
            if (!ObjectUtil.isVoid(phoneNumber)) {
                User user = Database.getTable(User.class).newRecord();
                user.setPhoneNumber(Phone.sanitizePhoneNumber(phoneNumber));
                user = Database.getTable(User.class).getRefreshed(user);
                if (!user.getRawRecord().isNewRecord()) {
                    instance.setBuyerId(user.getId());
                }
            }
        }
    }
}
