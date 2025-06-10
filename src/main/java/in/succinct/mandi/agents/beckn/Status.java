package in.succinct.mandi.agents.beckn;


import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Status extends BecknAsyncTask {


    public Status(Request request,Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        OnStatus onStatus = new OnStatus();
        onStatus.setContext(request.getContext());
        onStatus.setMessage(new Message());
        onStatus.getContext().setAction("on_status");

        String orderId = request.getMessage().get("order_id");
        if  (orderId == null){
            in.succinct.beckn.Order order = request.getMessage().getOrder();
            if (order != null){
                orderId = order.getId();
            }
        }
        long lOrderId = Long.parseLong(BecknUtil.getLocalUniqueId(orderId, Entity.order));
        Order order = null;
        if (lOrderId < 0){
            order  = Order.find(request.getContext().getTransactionId());
            if (order != null && !ObjectUtil.equals(order.getAttributeMap().get("external_order_id").getValue(),orderId)){
                order = null;
            }
        }else {
            order = Database.getTable(Order.class).get(lOrderId);
        }
        
        if (order == null){
            return onStatus;
        }


        in.succinct.beckn.Order becknOrder = OrderUtil.toBeckn(order, OrderFormat.order);

        onStatus.getMessage().setOrder(becknOrder);

        return(onStatus);
    }

}
