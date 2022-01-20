package in.succinct.mandi.agents.beckn.bap.delivery;

import com.venky.swf.plugins.beckn.tasks.BecknTask;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;

import java.util.Map;

public class OnStatus extends BecknTask {
    public OnStatus(Request request, Map<String, String> headers) {
        super(request, headers);
    }

    @Override
    public void execute() {
        in.succinct.beckn.Order order = getRequest().getMessage().getOrder();
        String orderId = order.getId();
        Order myorder = Order.find(orderId);
        if (myorder != null){
            if (order.getState().equals("COMPLETE")){
                myorder.deliver();
            }
        }
    }
}
