package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class OnOrderLineReturned implements Extension {
    static {
        Registry.instance().registerExtension("OrderLine."+ Order.RETURN_STATUS_RETURNED +".quantity", new OnOrderLineReturned());
    }

    @Override
    public void invoke(Object... context) {
        OrderLine orderLine = ((Model)context[0]).getRawRecord().getAsProxy(OrderLine.class);
        double qtyReturnedNow = orderLine.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter().valueOf(context[1]);
        Sku sku = orderLine.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        if (qtyReturnedNow > 0 && item.getAssetCodeId() != null && item.getAssetCode().isSac() && item.isItemRestrictedToSingleSeller() && item.isHumBhiOnlineSubscriptionItem() && orderLine.getDeliveredQuantity() > 0){
            // unpaid can be cancelled. Paid auto delivers.
            if (Database.getInstance().getCurrentUser().getId() != 1){
                throw new RuntimeException("Subscriptions are not refunded. It automatically stops once the amount is drained. ");
            }//Vi
            //Root user can cancel with discretion.
        }

    }
}
