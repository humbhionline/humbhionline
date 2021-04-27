package in.succinct.mandi.agents.beckn;


import com.fedex.ship.Measure;
import com.venky.swf.db.Database;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
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

    public Cancel(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        String order_id = request.getMessage().get("order_id");
        String cancel_reason_id = request.getMessage().get("cancellation_reason_id");


        Long lOrderId = Long.valueOf(BecknUtil.getLocalUniqueId(order_id, Entity.order));
        Long lCancelReasonId = Long.valueOf(BecknUtil.getLocalUniqueId(cancel_reason_id,Entity.cancellation_reason));

        OrderCancellationReason cancellationReason = Database.getTable(OrderCancellationReason.class).get(lCancelReasonId);
        Order order = Database.getTable(Order.class).get(lOrderId);
        order.cancel(cancellationReason.getReason(), OrderLine.CANCELLATION_INITIATOR_USER);

        in.succinct.beckn.Order becknOrder = new in.succinct.beckn.Order();
        becknOrder.setId(order_id);
        becknOrder.setCreatedAt(order.getCreatedAt());
        becknOrder.setUpdatedAt(order.getUpdatedAt());
        becknOrder.setState(order.getFulfillmentStatus());


        OnCancel onCancel = new OnCancel();
        onCancel.setContext(request.getContext());
        onCancel.setMessage(new Message());
        onCancel.getMessage().setOrder(becknOrder);


        new Call<JSONObject>().url(onCancel.getContext().getBapUri() + "/on_cancel").
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(onCancel.getInner()).headers(getHeaders(onCancel)).getResponseAsJson();

    }

    private Map<String, String> getHeaders(OnCancel onCancel) {
        Map<String,String> headers  = new HashMap<>();
        headers.put("Authorization",onCancel.generateAuthorizationHeader(onCancel.getContext().getBppId(),onCancel.getContext().getBppId() + ".k1"));
        return headers;
    }
}
