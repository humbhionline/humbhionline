package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import in.succinct.mandi.db.model.Order;

public class BeforeValidateOrder extends BeforeModelValidateExtension<Order> {
    static {
        registerExtension(new BeforeValidateOrder());
    }
    @Override
    public void beforeValidate(Order model) {
        if (model.isOnHold()){
            if (!model.getRawRecord().isFieldDirty("ON_HOLD")){
                if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") && !ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_CANCELLED)){
                    throw new RuntimeException("Order Status cannot change when on hold");
                }
            }
        }
    }
}
