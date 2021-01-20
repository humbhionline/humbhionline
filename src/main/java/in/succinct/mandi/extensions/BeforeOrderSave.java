package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import in.succinct.mandi.db.model.Order;

import java.io.IOException;

public class BeforeOrderSave extends BeforeModelSaveExtension<Order> {
    static {
        registerExtension(new BeforeOrderSave());
    }
    @Override
    public void beforeSave(Order model) {
        Order parentOrder = model.getParentOrder();
        Order transportOrder = model.getTransportOrder();

        if (parentOrder == null){
            // Is Product Order
            if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                    ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_CANCELLED)){
                if (transportOrder != null){
                    transportOrder.cancel("Original Order cancelled");
                }
            }
            return;
        }
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_DELIVERED)){
            parentOrder.deliver();
        }
        if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_SHIPPED)){
            parentOrder.ship();
        }

        if (model.getRawRecord().isNewRecord()){
            try {
                LuceneIndexer.instance(Order.class).updateDocument(parentOrder.getRawRecord());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


    }
}
