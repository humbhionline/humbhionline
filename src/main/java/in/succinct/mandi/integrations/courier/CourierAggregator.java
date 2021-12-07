package in.succinct.mandi.integrations.courier;

import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.integrations.courier.Courier.CourierOrder;
import in.succinct.mandi.integrations.courier.Courier.Quote;
import org.json.simple.JSONObject;

import java.util.List;

public interface CourierAggregator {
    public List<Quote> getQuotes(final Address from, final Address to, final Inventory inventory);
    public List<Quote> getQuotes(final Order retailOrder);
    public Order getOrder(JSONObject statusJson);
    public CourierOrder getCourierOrder(JSONObject statusJson);

    public CourierOrder book(Order transportOrder, Order parentOrder);
}
