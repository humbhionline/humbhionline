package in.succinct.mandi.extensions;

import com.venky.core.math.DoubleHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import in.succinct.mandi.db.model.Order;

public class BeforeValidateOrder extends BeforeModelValidateExtension<Order> {
    static {
        registerExtension(new BeforeValidateOrder());
    }
    @Override
    public void beforeValidate(Order model) {
        if (model.isOnHold()){
            if (!model.getRawRecord().isFieldDirty("ON_HOLD")){
                if (model.getRawRecord().isFieldDirty("FULFILLMENT_STATUS") && !ObjectUtil.equals(model.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_CANCELLED)){
                    throw new RuntimeException("Order Status cannot change when on hold");
                }
            }
        }
        if (!model.getRawRecord().isNewRecord() && model.getRawRecord().isFieldDirty("SHIPPING_SELLING_PRICE")) {
            //Delivery price edit support
            double shippingSellingPrice = model.getShippingSellingPrice();
            double shippingPrice = model.getShippingSellingPrice()/(1.0 + Order.GST_RATE_FOR_DELIVERY/100.0);
            double tax = shippingSellingPrice - shippingPrice;
            model.setShippingPrice(new DoubleHolder(shippingPrice,2).getHeldDouble().doubleValue());
            model.setPrice(model.getProductPrice() + model.getShippingPrice());
            model.setSellingPrice(model.getProductSellingPrice() + model.getShippingSellingPrice());
            if ( ObjectUtil.equals(model.getFacility().getStateId() , model.getShipToAddress().getStateId())){
                model.setCGst(new DoubleHolder(tax/2.0,2).getHeldDouble().doubleValue());
                model.setSGst(new DoubleHolder(tax/2.0,2).getHeldDouble().doubleValue());
                model.setIGst(0.0);
            }else {
                model.setCGst(0.0);
                model.setSGst(0.0);
                model.setIGst(new DoubleHolder(tax,2).getHeldDouble().doubleValue());
            }
        }
    }
}
