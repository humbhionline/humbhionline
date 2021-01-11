package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelUpdateExtension;
import in.succinct.mandi.db.model.Order;

public class BeforeOrderUpdate extends BeforeModelUpdateExtension<Order> {
    static {
        registerExtension(new BeforeOrderUpdate());
    }
    @Override
    public void beforeUpdate(Order model) {
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_DELIVERED)){
            Order refOrder = model.getRefOrder();
            if (refOrder != null){
                refOrder.deliver();
            }
        }
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_SHIPPED)){
            Order refOrder = model.getRefOrder();
            if (refOrder != null){
                refOrder.ship();
            }
        }
    }
}
