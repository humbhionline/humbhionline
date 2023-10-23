package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.Status;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import in.succinct.plugins.ecommerce.db.model.order.OrderAttribute;
import org.apache.commons.compress.harmony.pack200.BcBands;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
            Facility facility = model.getFacility();
            if (isBeingDelivered(model)){
                facility.notifyEvent(Facility.EVENT_TYPE_DELIVERED,model);
            }

            if (!facility.isCodEnabled() && isBeingPaid(model)) {
                facility.notifyEvent(Facility.EVENT_TYPE_BOOK_ORDER,model);
            }
            if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") || model.getRawRecord().isFieldDirty("AMOUNT_PAID") ||model.getRawRecord().isFieldDirty("AMOUNT_REFUNDED") ){
                Status status = createFakeStatusTask(model);
                if (status != null) {
                    TaskManager.instance().executeAsync(status, false);
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
    private Status createFakeStatusTask(Order model){
        if (ObjectUtil.isVoid(model.getExternalPlatformId()) || ObjectUtil.isVoid(model.getExternalTransactionReference())){
            return null;
        }
        Map<String,OrderAttribute> attributeMap = model.getAttributeMap();
        OrderAttribute selfPlatformAttribute = attributeMap.get("self_platform_url");
        if (selfPlatformAttribute == null || selfPlatformAttribute.getValue() == null){
            return null;
        }

        BecknNetwork network = BecknNetwork.findByRetailBppUrl(selfPlatformAttribute.getValue().substring(Config.instance().getServerBaseUrl().length()).replace("//","/"));
        Subscriber subscriber = network.getRetailBppSubscriber();

        Map<String,String> headers = new HashMap<>();
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        headers.put("Accept", MimeType.APPLICATION_JSON.toString());

        Request request = new Request();
        Context context = new Context();
        Message message = new Message();
        request.setMessage(message);request.setContext(context);
        in.succinct.beckn.Order becknOrder = OrderUtil.toBeckn(model, OrderFormat.order);
        message.setOrder(becknOrder);

        context.setBapId(attributeMap.get("external_platform_id").getValue());
        context.setBapUri(attributeMap.get("external_platform_url").getValue());
        context.setBppId(subscriber.getSubscriberId());
        context.setBppUri(subscriber.getSubscriberUrl());
        context.setAction("status");
        context.setMessageId(UUID.randomUUID().toString());
        context.setTransactionId(model.getExternalTransactionReference());
        context.setTimestamp(new Date());
        context.setCountry(model.getFacility().getCountry().getIsoCode());
        context.setCity(model.getFacility().getCity().getCode());
        context.setCoreVersion("0.9.3");
        Status status = new Status(request,headers);
        status.setSubscriber(subscriber);
        status.registerSignatureHeaders("Authorization");
        return status;
    }
    private boolean isBeingPaid(Order model){
        return (!model.getRawRecord().isNewRecord() && model.getRawRecord().isFieldDirty("AMOUNT_PAID") && model.getAmountPaid() > 0 && model.getAmountPendingPayment() == 0);
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
