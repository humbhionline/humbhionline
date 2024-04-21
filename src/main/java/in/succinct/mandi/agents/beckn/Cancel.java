package in.succinct.mandi.agents.beckn;


import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import in.succinct.beckn.CancellationReasons.CancellationReasonCode;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnCancel;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.util.Map;

public class Cancel extends BecknAsyncTask {

    public Cancel(Request request,Map<String,String> headers){
        super(request,headers);
    }

    @Override
    public Request executeInternal() {
        Request request = getRequest();
        OnCancel onCancel = new OnCancel();
        onCancel.setContext(request.getContext());
        onCancel.setMessage(new Message());
        onCancel.getContext().setAction("on_cancel");

        String order_id = request.getMessage().get("order_id");
        long lOrderId = Long.parseLong(BecknUtil.getLocalUniqueId(order_id, Entity.order));

        String cancel_reason_id = request.getMessage().get("cancellation_reason_id");

        Order order = Database.getTable(Order.class).get(lOrderId);
        if (order == null){
            return onCancel;
        }

        CancellationReasonCode cancellationReasonCode = null;
        if (!ObjectUtil.isVoid(cancel_reason_id)){
            cancellationReasonCode = CancellationReasonCode.convertor.valueOf(cancel_reason_id);
        }

        order.cancel(cancellationReasonCode == null ? null : cancellationReasonCode.name(), OrderLine.CANCELLATION_INITIATOR_USER);


        in.succinct.beckn.Order becknOrder = new in.succinct.beckn.Order();
        becknOrder.setId(order_id);
        becknOrder.setCreatedAt(order.getCreatedAt());
        becknOrder.setUpdatedAt(order.getUpdatedAt());
        becknOrder.setState(OrderUtil.getBecknStatus(order));


        onCancel.getMessage().setOrder(becknOrder);

        return (onCancel);
    }



}
