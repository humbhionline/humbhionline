package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;

import java.util.Map;

public class Confirm extends BecknAsyncTask{
    public Confirm(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        Context context = request.getContext();

        OnConfirm onConfirm = new OnConfirm();
        onConfirm.setContext(context);
        onConfirm.setMessage(new Message());
        onConfirm.getContext().setAction("on_confirm");

        Order order = Order.find(context.getTransactionId());
        if (order == null){
            return onConfirm;
        }
        order.setOnHold(false);
        in.succinct.beckn.Order becknOrder = request.getMessage().getOrder();
        Payment payment = becknOrder == null || becknOrder.getPayments() == null || becknOrder.getPayments().isEmpty() ? null : becknOrder.getPayments().get(0);
        if (payment != null && payment.getStatus() == PaymentStatus.PAID){
            order.setAmountPaid(payment.getParams().getAmount());
        }
        order.save();

        onConfirm.getMessage().setOrder(OrderUtil.toBeckn(order, OrderFormat.order));
        return (onConfirm);
    }
}
