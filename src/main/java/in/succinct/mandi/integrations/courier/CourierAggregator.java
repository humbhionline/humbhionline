package in.succinct.mandi.integrations.courier;

import in.succinct.beckn.Category;
import in.succinct.beckn.Context;
import in.succinct.beckn.Item;
import in.succinct.beckn.Provider;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import org.json.simple.JSONObject;

import java.util.List;

public interface CourierAggregator {
    //This is shown in inventory search against a retail order.
    public List<CourierQuote> getQuotes(final Order retailOrder);

    public CourierOrder book(Inventory inventory, Order parentOrder);

    public Order getOrder(JSONObject statusJson);
    public CourierOrder getCourierOrder(JSONObject statusJson);



    public static interface CourierQuote {
        Item getItem();
        Provider getProvider();
        Context getContext();
        Category getCategory();
    }
    public static interface CourierOrder {
        in.succinct.beckn.Order getOrder();
        Context getContext();
    }
}
