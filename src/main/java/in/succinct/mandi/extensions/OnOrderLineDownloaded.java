package in.succinct.mandi.extensions;

import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class OnOrderLineDownloaded implements Extension {
    static {
        Registry.instance().registerExtension("OrderLine."+ Order.FULFILLMENT_STATUS_DOWNLOADED +".quantity", new OnOrderLineDownloaded());
    }

    @Override
    public void invoke(Object... context) {
        OrderLine orderLine = ((Model)context[0]).getRawRecord().getAsProxy(OrderLine.class);
        double qtyDownloadedNow = orderLine.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter().valueOf(context[1]);
        Sku sku = orderLine.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        User vendor = orderLine.getShipFrom().getCreatorUser().getRawRecord().getAsProxy(User.class);
        if (qtyDownloadedNow > 0 && !item.isHumBhiOnlineSubscriptionItem() && CompanyUtil.isHumBhiOnlineSubscriptionItemPresent()){
            vendor.setBalanceOrderLineCount(vendor.getBalanceOrderLineCount() - 1);
            vendor.save();
        }

    }
}
