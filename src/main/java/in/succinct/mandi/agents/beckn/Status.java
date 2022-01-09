package in.succinct.mandi.agents.beckn;


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


    public Status(Request request){
        super(request);
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        OnStatus onStatus = new OnStatus();
        onStatus.setContext(request.getContext());
        onStatus.setMessage(new Message());
        onStatus.getContext().setAction("on_status");

        String orderId = request.getMessage().get("order_id");
        Long lOrderId = Long.valueOf(BecknUtil.getLocalUniqueId(orderId, Entity.order));

        Order order = Database.getTable(Order.class).get(lOrderId);
        if (order == null){
            return onStatus;
        }


        in.succinct.beckn.Order becknOrder = OrderUtil.toBeckn(order, OrderFormat.order);

        onStatus.getMessage().setOrder(becknOrder);

        return(onStatus);
    }

}
