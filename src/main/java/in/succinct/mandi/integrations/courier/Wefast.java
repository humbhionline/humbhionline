package in.succinct.mandi.integrations.courier;

import com.venky.core.util.Bucket;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class Wefast {
    public double getPrice(Order order){
        return 0;
    }
    public void createOrder(Order order){
        JSONObject orderJson = makeJson(order);
        Call<JSONObject> call = new Call<JSONObject>().url(Config.instance().getProperty("wefast.api.url") ,
                "/create-order").
                header("X-DV-Auth-Token",Config.instance().getProperty("wefast.api.token")).
                inputFormat(InputFormat.JSON).input(orderJson);

        if (call.hasErrors()){
            throw new RuntimeException(call.getError());
        }

        JSONObject response = call.getResponseAsJson();

    }

    private JSONObject makeJson(Order order) {
        JSONObject obj = new JSONObject();
        Set<String> tags = new HashSet<>();
        Bucket weight = new Bucket();
        for (OrderLine line: order.getOrderLines()){
            StringTokenizer tok = new StringTokenizer(line.getInventory().getRawRecord().getAsProxy(Inventory.class).getTags());
            while (tok.hasMoreTokens()){
                tags.add(tok.nextToken());
            }

        }
        obj.put("matter",tags.toString());
        obj.put("total_weight", UnitOfMeasureConversionTable.convert(order.getWeight(), UnitOfMeasure.MEASURES_WEIGHT,order.getWeightUom(),
                UnitOfMeasure.getMeasure(UnitOfMeasure.MEASURES_WEIGHT,"Kgs")));
        obj.put("payment_method","cash");
        JSONArray points = new JSONArray();
        obj.put("points",points);
        points.put(makeJson(order.getFacility(),order.getAddresses()));
        return obj;
    }

    private List<JSONObject> makeJson(Facility shipFrom, List<OrderAddress> addresses) {
        OrderAddress shipTo = addresses.stream().filter(a->a.getAddressType().equals(OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get();
        for (Address address : new Address[]{shipFrom,shipTo}){

        }
        JSONObject p1 = new JSONObject();
        StringBuilder address = new StringBuilder();
        p1.put("address",shipFrom.getName() + "," + shipFrom.getAddressLine1());

        return Arrays.asList(p1);
    }
}
