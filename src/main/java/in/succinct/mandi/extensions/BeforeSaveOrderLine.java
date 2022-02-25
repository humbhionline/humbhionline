package in.succinct.mandi.extensions;

import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import in.succinct.mandi.agents.SyncPrice;
import in.succinct.mandi.agents.beckn.Select;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.io.IOException;

public class BeforeSaveOrderLine extends BeforeModelSaveExtension<OrderLine> {
    static {
        registerExtension(new BeforeSaveOrderLine());
    }
    @Override
    public void beforeSave(OrderLine model) {
        try {
            //Reindex order for virtual fields.
            if (isBeingCancelled(model)){
                model.getShipFrom().getRawRecord().getAsProxy(Facility.class).notifyEvent(Facility.EVENT_TYPE_CANCEL_ORDER_LINE,model);
            }
            if (!model.getRawRecord().isNewRecord() &&  null == Database.getInstance().getCurrentTransaction().getAttribute("SyncPriceInProgress")){
                TaskManager.instance().executeAsync(new SyncPrice(model.getOrderId()),false);
            }
            LuceneIndexer.instance(Order.class).updateDocument(model.getOrder().getRawRecord());
        }catch (IOException ex){
            throw new RuntimeException(ex);
        }
    }

    private boolean isBeingCancelled(OrderLine model) {
        return (model.getCancelledQuantity() + model.getReturnedQuantity() > 0 && model.getRemainingCancellableQuantity() == 0
                && ( model.getRawRecord().isFieldDirty("CANCELLED_QUANTITY") || model.getRawRecord().isFieldDirty("RETURNED_QUANTITY")) );
    }
}
