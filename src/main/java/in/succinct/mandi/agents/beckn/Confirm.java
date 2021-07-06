package in.succinct.mandi.agents.beckn;

import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Confirm extends BecknAsyncTask{
    public Confirm(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        Context context = request.getContext();
        List<Order> orders = new Select().from(Order.class).where(new Expression(ModelReflector.instance(Order.class).getPool(), "EXTERNAL_TRANSACTION_REFERENCE", Operator.EQ,context.getTransactionId())).execute();
        if (orders.size() != 1){
            throw new RuntimeException("Invalid Transaction Id");
        }
        Order order = orders.get(0);
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

        new Call<JSONObject>().url(getRequest().getContext().getBapUri() + "/on_confirm").
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(onConfirm.getInner()).headers(getHeaders(onConfirm)).getResponseAsJson();

    }
    private Map<String, String> getHeaders(Request request) {
        Map<String,String> headers  = new HashMap<>();
        headers.put("Authorization",request.generateAuthorizationHeader(request.getContext().getBppId(),request.getContext().getBppId() + ".k1"));
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        headers.put("Accept", MimeType.APPLICATION_JSON.toString());

        return headers;
    }
}
