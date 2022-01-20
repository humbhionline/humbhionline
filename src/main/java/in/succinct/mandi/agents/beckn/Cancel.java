package in.succinct.mandi.agents.beckn;


import com.fedex.ship.Measure;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnCancel;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import org.json.simple.JSONObject;

import java.util.HashMap;
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
        String cancel_reason_id = request.getMessage().get("cancellation_reason_id");


        Long lOrderId = Long.valueOf(BecknUtil.getLocalUniqueId(order_id, Entity.order));
        Long lCancelReasonId = Long.valueOf(BecknUtil.getLocalUniqueId(cancel_reason_id,Entity.cancellation_reason));

        Order order = Database.getTable(Order.class).get(lOrderId);
        if (order == null){
            return onCancel;
        }
        OrderCancellationReason cancellationReason = Database.getTable(OrderCancellationReason.class).get(lCancelReasonId);
        order.cancel(cancellationReason.getReason(), OrderLine.CANCELLATION_INITIATOR_USER);

        in.succinct.beckn.Order becknOrder = new in.succinct.beckn.Order();
        becknOrder.setId(order_id);
        becknOrder.setCreatedAt(order.getCreatedAt());
        becknOrder.setUpdatedAt(order.getUpdatedAt());
        becknOrder.setState(order.getFulfillmentStatus());


        onCancel.getMessage().setOrder(becknOrder);

        return (onCancel);
    }

}
