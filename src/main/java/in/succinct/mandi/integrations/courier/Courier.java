package in.succinct.mandi.integrations.courier;

import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import org.json.simple.JSONObject;

public interface Courier {
    public Quote getQuote(final Address from, final Address to, final Inventory inventory);

    public Quote getQuote(final Order retailOrder);
    public CourierOrder book(final Order transportOrder, final Order retailOrder);
    public Order getOrder(JSONObject statusJson);
    public CourierOrder getCourierOrder(JSONObject statusJson);

    public interface CourierDescriptor {
        public String getId();
        public String getName();
        public String getLogoUrl();
    }


    public interface Quote {
        default String getQuoteRef(){
            return null;
        }
        public CourierDescriptor getCourierDescriptor();
        public double getSellingPrice();
        default double getDiscount(){
            return 0.0;
        }
        default double getPrice(){
            return getSellingPrice() - getDiscount();
        }
    }
    public interface CourierOrder extends Quote{
        public String getTrackingUrl();
        public String getOrderNumber();
        boolean isCompleted();
    }

}
