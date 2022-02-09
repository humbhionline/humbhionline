
package in.succinct.mandi.agents.beckn;


import com.venky.swf.db.Database;
import in.succinct.beckn.Message;
import in.succinct.beckn.Order;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Update extends Init {

    public Update(Request request, Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Request on_update = new Request();
        on_update.setContext(getRequest().getContext());
        on_update.setMessage(new Message());
        String targets = getRequest().getMessage().get("update_target");
        Order becknOrder = getRequest().getMessage().getOrder();
        String orderId  = BecknUtil.getLocalUniqueId(becknOrder.getId(), Entity.order);
        in.succinct.mandi.db.model.Order order = Database.getTable(in.succinct.mandi.db.model.Order.class).get(Long.parseLong(orderId));

        Arrays.stream(targets.split(",")).forEach(target->{
            switch (target) {
                case "billing":
                    createBillTo(order, becknOrder.getBilling());
                    break;
                case "fulfillment":
                    createShipTo(order, becknOrder.getFulfillment());
                    break;
                case "items":
                    //
                    Map<Long, OrderLine> map = new HashMap<>();
                    order.getOrderLines().forEach(l -> map.put(l.getSkuId(), l));


                    becknOrder.getItems().forEach(item -> {
                        long invId = Long.parseLong(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
                        Inventory inventory = Database.getTable(Inventory.class).get(invId);
                        if (inventory == null) {
                            throw new RuntimeException("Invalid Item!");
                        }

                        Quantity finalQuantity = item.getQuantity();
                        int count = finalQuantity.getCount();
                        OrderLine line = map.get(inventory.getSkuId());
                        if (line == null) {
                            throw new RuntimeException("Item not ordered");
                        }
                        if (count > line.getRemainingCancellableQuantity()) {
                            throw new RuntimeException("Quantity cannot be increased");
                        }
                        double quantityCancelledNow = line.getRemainingCancellableQuantity() - count;
                        if (quantityCancelledNow < 0) {
                            throw new RuntimeException("Cannot cancel more than " + line.getRemainingCancellableQuantity());
                        }
                        if (quantityCancelledNow > 0) {
                            line.cancel(OrderLine.CANCELLATION_REASON_NOT_REQUIRED, OrderLine.CANCELLATION_INITIATOR_USER, line.getRemainingCancellableQuantity() - count);
                        }
                    });
                    break;
                default:
                    throw new RuntimeException("Modification not Allowed for " + target);
            }

        });

        on_update.getMessage().setOrder(OrderUtil.toBeckn(order, OrderFormat.order));
        return on_update;
    }
}
