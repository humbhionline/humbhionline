package in.succinct.mandi.db.model;

import com.venky.core.util.Bucket;
import com.venky.swf.db.table.ModelImpl;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class OrderImpl extends ModelImpl<Order> {
    public OrderImpl(Order proxy){
        super(proxy);

    }
    public OrderImpl(){
        super();
    }
    public void completePayment(boolean save) {
        Order order = getProxy();
        order.setAmountPaid(order.getAmountPendingPayment());
        for (OrderLine line : order.getOrderLines()){
            Item item = line.getSku().getItem().getRawRecord().getAsProxy(Item.class);
            AssetCode assetCode = item.getAssetCodeId() == null ? null : item.getAssetCode();
            if (assetCode != null && assetCode.isSac()){
                if (line.getToAcknowledgeQuantity() > 0) {
                    line.acknowledge();
                }
                if (line.getToPackQuantity() > 0) {
                    line.pack(line.getToPackQuantity());
                }
                double toShipQuantity = line.getToShipQuantity();
                if (toShipQuantity > 0 ){
                    line.ship(toShipQuantity);
                    line.deliver(toShipQuantity);
                }
                //Auto pack,ship and deliver service lines.
            }

        }
        order.resetPayment(save);
    }


    public void completeRefund(boolean save){
        Order order = getProxy();
        if (order.getAmountPaid() > 0){
            order.setAmountRefunded(order.getAmountToRefund());
            order.resetRefund(save);
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
    public void resetPayment(boolean save){
        Order order = getProxy();
        order.setPaymentInitialized(false);
        if (save){
            order.save();
        }
    }
    public void resetRefund(boolean save){
        Order order = getProxy();
        order.setRefundInitialized(false);
        if (save){
            order.save();
        }
    }


}
