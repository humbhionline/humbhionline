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
}
