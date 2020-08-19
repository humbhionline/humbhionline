package in.succinct.mandi.db.model;

import com.venky.swf.db.table.ModelImpl;

public class OrderImpl extends ModelImpl<Order> {
    public OrderImpl(Order proxy){
        super(proxy);

    }
    public OrderImpl(){
        super();
    }
    public void completePayment() {
        Order order = getProxy();
        order.setPaid(true);
        order.save();
    }
    public void initiatePayment(){
        Order order = getProxy();
        order.setPaymentInitiated(true);
        order.save();
    }

    public void resetPayment(){
        Order order = getProxy();
        if (order.isPaid()){
            order.setPaid(false);
        }else if (order.isPaymentInitiated()){
            order.setPaymentInitiated(false);
        }
        order.save();
    }

    public void returnPayment(){
        Order order = getProxy();
        if (order.isPaid()){
            order.setReturned(true);
            order.save();
        }
    }

}
