package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.model.Model;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class OnOrderLineDelivery implements Extension {
    static {
        Registry.instance().registerExtension("OrderLine."+ Order.FULFILLMENT_STATUS_DELIVERED +".quantity", new OnOrderLineDelivery());
    }

    @Override
    public void invoke(Object... context) {
        OrderLine orderLine = ((Model)context[0]).getRawRecord().getAsProxy(OrderLine.class);
        double qtyDeliveredNow = orderLine.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter().valueOf(context[1]);
        Sku sku = orderLine.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        if (qtyDeliveredNow > 0 && item.getAssetCode().isSac() && item.isItemRestrictedToSingleSeller() && item.isHumBhiOnlineSubscriptionItem()){
            Order order = orderLine.getOrder().getRawRecord().getAsProxy(Order.class);
            if (order.getAmountPendingPayment() > 0){
                throw new RuntimeException("Delivery happens after accepting the payment automatically. Just accept the payment.");
            }
            User creator = orderLine.getCreatorUser().getRawRecord().getAsProxy(User.class);
            UnitOfMeasure pack = sku.getPackagingUOM();
            double numberOfLines = UnitOfMeasureConversionTable.convert(qtyDeliveredNow, pack.getMeasures(), pack.getName() ,"Single");
            creator.setBalanceOrderLineCount(creator.getBalanceOrderLineCount() + numberOfLines);
            creator.save();
        }

    }
}
