package in.succinct.mandi.extensions;

import com.venky.core.math.DoubleUtils;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

public class BeforeValidateOrderLine  extends BeforeModelValidateExtension<OrderLine> {
    static {
        registerExtension(new BeforeValidateOrderLine());
    }
    @Override
    public void beforeValidate(OrderLine orderLine) {
        TypeConverter<Double> dConvertor = orderLine.getReflector().getJdbcTypeHelper().getTypeRef(Double.class).getTypeConverter();

        Double newCancelledQty = orderLine.getCancelledQuantity();
        Double newReturnedQty  = orderLine.getReturnedQuantity();

        Double oldCancelledQty = orderLine.getRawRecord().isFieldDirty("CANCELLED_QUANTITY")? dConvertor.valueOf(orderLine.getRawRecord().getOldValue("CANCELLED_QUANTITY")) : orderLine.getCancelledQuantity() ;
        Double oldReturnedQty  = orderLine.getRawRecord().isFieldDirty("RETURNED_QUANTITY") ? dConvertor.valueOf(orderLine.getRawRecord().getOldValue("RETURNED_QUANTITY")) : orderLine.getReturnedQuantity();

        double qtyCancelledNow = newCancelledQty - oldCancelledQty ;
        double qtyReturnedNow  = newReturnedQty  - oldReturnedQty;
        if (qtyCancelledNow + qtyReturnedNow <= 0){
            return;
        }

        Sku sku = orderLine.getSku();
        Item item = sku.getItem().getRawRecord().getAsProxy(Item.class);
        User vendor = null;
        boolean isItemHumBhiOnlineSubscription = item.isHumBhiOnlineSubscriptionItem() && item.isItemRestrictedToSingleSeller() && item.getAssetCodeId() != null && item.getAssetCode().isSac();

        if (!isItemHumBhiOnlineSubscription) {
            vendor = orderLine.getShipFrom().getCreatorUser().getRawRecord().getAsProxy(User.class);
            boolean fullyCancelled = true;
            if (DoubleUtils.compareTo(orderLine.getRemainingCancellableQuantity(),0) > 0) {
                fullyCancelled = false;
            }
            if (fullyCancelled){
                vendor.setBalanceOrderLineCount(vendor.getBalanceOrderLineCount() + 1);
            }
        }else if (orderLine.getDeliveredQuantity() > 0){
            vendor = orderLine.getCreatorUser().getRawRecord().getAsProxy(User.class);
            UnitOfMeasure pack = sku.getPackagingUOM();
            double cf = UnitOfMeasureConversionTable.convert(1,pack.getMeasures(),pack.getName(),"Single");

            if (vendor.getBalanceOrderLineCount() < qtyCancelledNow * cf) {
                qtyCancelledNow = Math.floor(vendor.getBalanceOrderLineCount() / cf);
                newCancelledQty = ( oldCancelledQty + qtyCancelledNow);
                orderLine.setCancelledQuantity(newCancelledQty);
            }
            vendor.setBalanceOrderLineCount(vendor.getBalanceOrderLineCount() - qtyCancelledNow * cf);
            if (vendor.getBalanceOrderLineCount() < qtyReturnedNow * cf) {
                qtyReturnedNow = Math.floor(vendor.getBalanceOrderLineCount() / cf);
                newReturnedQty = ( oldCancelledQty + qtyReturnedNow);
                orderLine.setReturnedQuantity(newReturnedQty);
            }
            vendor.setBalanceOrderLineCount(vendor.getBalanceOrderLineCount() - qtyReturnedNow *cf);
            if (qtyCancelledNow + qtyReturnedNow <= 0){
                throw new RuntimeException("Nothing left to cancel");
            }
        }
        if (vendor != null) {
            vendor.save();
        }

    }
}
