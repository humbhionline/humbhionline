package in.succinct.mandi.agents.beckn;


import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.BreakUp.BreakUpElement.BreakUpCategory;
import in.succinct.beckn.Item;
import in.succinct.beckn.ItemQuantity;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.Order;
import in.succinct.beckn.Order.NonUniqueItems;
import in.succinct.beckn.Price;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;

import java.util.Map;

public class Select extends BecknAsyncTask {

    public Select(Request request,Map<String,String> headers){
        super(request,headers);
    }
    boolean forMyShard(Order selected){
        if (selected == null){
            return false;
        }
        NonUniqueItems items = selected.getItems();
        if (items == null || items.isEmpty()){
            return false;
        }
        long invId = Long.parseLong(BecknUtil.getLocalUniqueId(items.get(0).getId(), Entity.item));
        if (invId > 0){
            return Database.getTable(Inventory.class).get(invId) != null;
        }
        return false;
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        OnSelect onSelect = new OnSelect();
        onSelect.setContext(request.getContext());
        onSelect.getContext().setAction("on_select");

        Message outMessage = new Message();
        onSelect.setMessage(outMessage);

        Order selected = request.getMessage().getSelected();
        if (!forMyShard(selected)){
            return onSelect;
        }

        Order outSelected = new Order();
        outMessage.setSelected(outSelected);

        in.succinct.mandi.db.model.Order order = in.succinct.mandi.db.model.Order.find(getRequest().getContext().getTransactionId());
        if (order != null){
            if (order.isOnHold() && ObjectUtil.equals(order.getFulfillmentStatus(), in.succinct.mandi.db.model.Order.FULFILLMENT_STATUS_DOWNLOADED)){
                order.setOnHold(false);
                order.setExternalTransactionReference("");
                order.save();
                order.cancel("Cart Dropped");
            }else {
                throw new RuntimeException("Transaction already confirmed!");
            }
        }



        NonUniqueItems outItems = new NonUniqueItems();
        outSelected.setItems(outItems);

        //select changes jul 7 Long facilityId = Long.valueOf(BecknUtil.getLocalUniqueId(String.valueOf(location.getId()),Entity.provider_location));

        NonUniqueItems items = selected.getItems();

        Bucket itemPrice = new Bucket();
        Bucket listedPrice = new Bucket();

        for (int i = 0 ; i < items.size() ; i ++ ){
            Item item = items.get(i);
            Item outItem = new Item();
            outItem.setId(item.getId());

            long invId = Long.parseLong(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
            Quantity quantity = item.get(Quantity.class,"quantity");


            ItemQuantity outQuantity = new ItemQuantity();
            outItem.set("quantity",outQuantity);

            outQuantity.setSelected(quantity);

            Inventory inventory = Database.getTable(Inventory.class).get(invId);
            if (inventory == null || inventory.getRawRecord().isNewRecord()){
                throw new RuntimeException("No inventory with provider.");
            }
            itemPrice.increment(inventory.getSellingPrice() * quantity.getCount());
            listedPrice.increment(inventory.getMaxRetailPrice() * quantity.getCount());

            Price price = new Price();
            outItem.setPrice(price);
            price.setCurrency("INR");
            price.setListedValue(inventory.getMaxRetailPrice() * quantity.getCount());
            price.setOfferedValue(inventory.getSellingPrice() * quantity.getCount());
            price.setValue(inventory.getSellingPrice() * quantity.getCount());

            item.set("tags",OrderUtil.getTags(inventory));

            outItems.add(outItem);
        }

        Quote quote = new Quote();
        outSelected.setQuote(quote);

        Price price = new Price();
        quote.setPrice(price);
        price.setListedValue(listedPrice.value());
        price.setValue(listedPrice.value());
        price.setCurrency("INR");
        /* /select  jul7
        if (DoubleUtils.compareTo(listedPrice.value(), itemPrice.value() ,2)>0){
            price.setOfferedValue(itemPrice.value());
        }*/

        price.setOfferedValue(itemPrice.value());
        quote.setTtl(15L*60L); //15 minutes.

        BreakUp breakUp = new BreakUp();
        BreakUpElement element = breakUp.createElement(BreakUpCategory.item,"Total Product",price);
        breakUp.add(element);
        quote.setBreakUp(breakUp);

        return(onSelect);

    }


}
