package in.succinct.mandi.db.model;

import com.venky.core.util.Bucket;
import com.venky.swf.db.table.ModelImpl;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class OrderImpl extends ModelImpl<Order> {
    public OrderImpl(Order proxy){
        super(proxy);

    }
    public OrderImpl(){
        super();
    }
    public void completePayment() {
        Order order = getProxy();
        order.setAmountPaid(order.getAmountPendingPayment());
        order.resetPayment();
    }


    public void completeRefund(){
        Order order = getProxy();
        if (order.getAmountPaid() > 0){
            order.setAmountRefunded(order.getAmountToRefund());
            order.resetRefund();
        }
    }
    public double getAmountPendingPayment(){
        Order order = getProxy();
        Bucket netPayment = new Bucket(0.0);
        for (OrderLine orderLine : order.getOrderLines()) {
            double toPayQuantity = orderLine.getOrderedQuantity() - orderLine.getCancelledQuantity() - orderLine.getReturnedQuantity() ;
            netPayment.increment(toPayQuantity * orderLine.getSellingPrice()/orderLine.getOrderedQuantity());
        }
        return  netPayment.doubleValue() - order.getAmountPaid() + order.getAmountRefunded();
    }

    public double getAmountToRefund(){
        return -1 * getAmountPendingPayment();
    }

    public void initializePayment(){
        Order order = getProxy();
        order.setPaymentInitialized(true);
        order.save();
    }
    public void initializeRefund(){
        Order order = getProxy();
        order.setRefundInitialized(true);
        order.save();
    }
    public void resetPayment(){
        Order order = getProxy();
        order.setPaymentInitialized(false);
        order.save();
    }
    public void resetRefund(){
        Order order = getProxy();
        order.setRefundInitialized(false);
        order.save();
    }

}
