package in.succinct.mandi.db.model;

import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OrderImpl extends ModelImpl<Order> {
    public OrderImpl(Order proxy) {
        super(proxy);

    }

    public OrderImpl() {
        super();
    }

    public void completePayment(boolean save) {
        Order order = getProxy();
        order.setAmountPaid(order.getAmountPaid() + order.getAmountPendingPayment());
        for (OrderLine line : order.getOrderLines()) {
            Item item = line.getSku().getItem().getRawRecord().getAsProxy(Item.class);
            AssetCode assetCode = item.getAssetCodeId() == null ? null : item.getAssetCode();
            if (assetCode != null && assetCode.isSac() && (item.isHumBhiOnlineSubscriptionItem()  || assetCode.isDeliveredElectronically())) {
                line.deliver();
            }
        }
        order.resetPayment(save);
    }


    public void completeRefund(boolean save) {
        Order order = getProxy();
        if (order.getAmountPaid() > 0) {
            order.setAmountRefunded(order.getAmountRefunded() + order.getAmountToRefund());
            order.resetRefund(save);
        }
    }

    public double getAmountPendingPayment() {
        Order order = getProxy();
        Bucket netPayment = new Bucket(0.0);
        boolean anyThingDelivered = false;
        for (OrderLine orderLine : order.getOrderLines()) {
            double toPayQuantity = orderLine.getOrderedQuantity() - orderLine.getCancelledQuantity() - orderLine.getReturnedQuantity();
            netPayment.increment(toPayQuantity * orderLine.getSellingPrice() / orderLine.getOrderedQuantity());
            anyThingDelivered = anyThingDelivered || orderLine.getDeliveredQuantity() > 0;
        }
        if (anyThingDelivered) {
            netPayment.increment(order.getShippingSellingPrice());
        }
        netPayment.decrement(order.getAmountPaid());
        netPayment.increment(order.getAmountRefunded());

        return netPayment.doubleValue();
    }

    public double getAmountToRefund() {
        return -1 * getAmountPendingPayment();
    }

    public void initializePayment() {
        Order order = getProxy();
        order.setPaymentInitialized(true);
        order.save();
    }

    public void initializeRefund() {
        Order order = getProxy();
        order.setRefundInitialized(true);
        order.save();
    }

    public void resetPayment(boolean save) {
        Order order = getProxy();
        order.setPaymentInitialized(false);
        if (save) {
            order.save();
        }
    }

    public void resetRefund(boolean save) {
        Order order = getProxy();
        order.setRefundInitialized(false);
        if (save) {
            order.save();
        }
    }

    public boolean isDeliveryPlanned() {
        return getTransportOrder() != null;
    }

    public boolean isOpen(){
        Order order = getProxy();
        if (getAmountPendingPayment() > 0 || getAmountToRefund() > 0){
            return true;
        }
        for (OrderLine line : order.getOrderLines()){
            if (line.getToShipQuantity() > 0 || line.getToDeliverQuantity() > 0){
                return true;
            }
        }
        return false;
    }
    public boolean isCancelled(){
        Order order = getProxy();
        return ObjectUtil.equals(order.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_CANCELLED) || ObjectUtil.equals(order.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_RETURNED);
    }

    public Order getTransportOrder(){
        Order order = getProxy();
        List<Order> deliveryPlans = new Select().from(Order.class).where(new Expression(getReflector().getPool(), "PARENT_ORDER_ID", Operator.EQ, order.getId())).execute();
        Optional<Order> optionalDeliveryPlan = deliveryPlans.stream().filter(dp -> !dp.isCancelled() ).findAny();
        if (optionalDeliveryPlan.isPresent()){
            return optionalDeliveryPlan.get();
        }
        return null;
    }

    public Long getRefOrderId() {
        return getProxy().getParentOrderId();
    }
    public void setRefOrderId(Long id) {
        getProxy().setParentOrderId(id);
    }
    public RefOrder getRefOrder() {
        Order parent  = getProxy().getParentOrder() ;
        if (parent  != null ) {
            return parent.getRawRecord().getAsProxy(RefOrder.class);
        }else {
            return null;
        }
    }

    public Long getShipToAddressId(){
        loadAddress();
        return shipToAddress.getId();
    }
    public void setShipToAddressId(Long id){

    }
    public ShipToAddress getShipToAddress(){
        loadAddress();
        return shipToAddress.getRawRecord().getAsProxy(ShipToAddress.class);
    }

    public Long getBillToAddressId(){
        loadAddress();
        return billToAddress.getId();
    }
    public void setBillToAddressId(Long id){

    }
    public BillToAddress getBillToAddress(){
        loadAddress();
        return billToAddress.getRawRecord().getAsProxy(BillToAddress.class);
    }



    private in.succinct.plugins.ecommerce.db.model.order.OrderAddress shipToAddress = null;
    private in.succinct.plugins.ecommerce.db.model.order.OrderAddress billToAddress = null;
    public void loadAddress(){
        if ( shipToAddress == null || billToAddress == null){
            getProxy().getAddresses().forEach(a->{
                if (ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_SHIP_TO)){
                    shipToAddress = a;
                }else if (ObjectUtil.equals(a.getAddressType() ,OrderAddress.ADDRESS_TYPE_BILL_TO)) {
                    billToAddress = a;
                }
            });
        }
    }

}
