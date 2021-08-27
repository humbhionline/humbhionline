package in.succinct.mandi.agents.beckn;

import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import org.bouncycastle.cert.ocsp.Req;
import org.json.simple.JSONObject;

public class Confirm extends BecknAsyncTask{
    public Confirm(Request request){
        super(request);
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        Context context = request.getContext();
        Order order = Order.find(context.getTransactionId());
        if (order == null){
            throw new RuntimeException("Invalid Transaction Id");
        }
        order.setOnHold(false);
        in.succinct.beckn.Order becknOrder = request.getMessage().getOrder();
        if ("PAID".equals(becknOrder.getPayment().getStatus())){
            order.setAmountPaid(becknOrder.getPayment().getParams().getAmount());
            order.save();
        }

        OnConfirm onConfirm = new OnConfirm();
        onConfirm.setContext(context);
        onConfirm.setMessage(new Message());
        onConfirm.getMessage().setOrder(OrderUtil.toBeckn(order, OrderFormat.order));
        onConfirm.getContext().setAction("on_confirm");
        return (onConfirm);
    }
}
