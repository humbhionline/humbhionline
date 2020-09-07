package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import javax.xml.crypto.Data;

public class OnOrderLineCancel implements Extension {
    static {
        Registry.instance().registerExtension("OrderLine."+ Order.FULFILLMENT_STATUS_CANCELLED +".quantity", new OnOrderLineCancel());
    }

    @Override
    public void invoke(Object... context) {
        OrderLine orderLine = ((Model)context[0]).getRawRecord().getAsProxy(OrderLine.class);
        double qtyCancelledNow = orderLine.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter().valueOf(context[1]);
        Sku sku = orderLine.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        if (qtyCancelledNow > 0 && item.getAssetCode().isSac() && item.isItemRestrictedToSingleSeller() && item.isHumBhiOnlineSubscriptionItem() && orderLine.getDeliveredQuantity() > 0){
            // unpaid can be cancelled. Paid auto delivers.
            if (Database.getInstance().getCurrentUser().getId() != 1){
                throw new RuntimeException("Subscriptions are not refunded. It automatically stops once the amount is drained. ");
            }//
            //Root user can cancel with discretion.
        }

    }
}
