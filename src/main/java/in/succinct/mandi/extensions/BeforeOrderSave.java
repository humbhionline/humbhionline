package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import in.succinct.mandi.db.model.Facility;
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
            if (isBeingCancelled(model)){
                if (transportOrder != null){
                    transportOrder.cancel("Original Order cancelled");
                }
            }
            if (isBeingDelivered(model)){
                model.getFacility().notifyEvent(Facility.EVENT_TYPE_DELIVERED,model);
            }
            if (isBeingPaid(model)){
                model.getFacility().notifyEvent(Facility.EVENT_TYPE_BOOK_ORDER,model);
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
    private boolean isBeingPaid(Order model){
        return (model.getRawRecord().isFieldDirty("AMOUNT_PAID") && model.getAmountPendingPayment() == 0);
    }
    private boolean isBeingDelivered(Order model) {
        return (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(), Order.FULFILLMENT_STATUS_DELIVERED));
    }

    private boolean isBeingCancelled(Order model) {
        return (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(), Order.FULFILLMENT_STATUS_CANCELLED));
    }

    private boolean isBeingShipped(Order model){
        return (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") &&
                ObjectUtil.equals(model.getFulfillmentStatus(), Order.FULFILLMENT_STATUS_SHIPPED));
    }

}
