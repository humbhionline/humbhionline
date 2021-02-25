package in.succinct.mandi.integrations.courier;

import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
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
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class Wefast {
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


    public double getPrice(Order order){
        JSONObject orderJson = makeJson(order);
        Call<JSONObject> call = new Call<JSONObject>().url(Config.instance().getProperty("wefast.api.url") ,
                "/calculate-order").
                header("X-DV-Auth-Token",Config.instance().getProperty("wefast.api.token")).
                inputFormat(InputFormat.JSON).input(orderJson);

        if (call.hasErrors()){
            throw new RuntimeException(call.getError());
        }

        JSONObject response = call.getResponseAsJson();
        Boolean success = (Boolean)response.get("is_successful");
        if (success){
            JSONObject jsonResponseOrder = (JSONObject) response.get("order");
            return (Double)jsonResponseOrder.get("payment_amount");
        }
        return Double.POSITIVE_INFINITY;
    }

    private JSONObject makeJson(Order order) {
        JSONObject obj = new JSONObject();
        Set<String> tags = new HashSet<>();
        Bucket weight = new Bucket();
        JSONArray packages = new JSONArray();

        for (OrderLine line: order.getOrderLines()){
            StringTokenizer tok = new StringTokenizer(line.getInventory().getRawRecord().getAsProxy(Inventory.class).getTags());
            while (tok.hasMoreTokens()){
                tags.add(tok.nextToken());
            }
        }
        JSONObject p = new JSONObject();
        p.put("ware_code",order.getFacility().getName());
        p.put("description", "HumBhiOnline Order# " + order.getId());
        p.put("items_count", order.getOrderLines().size());
        packages.add(p);

        obj.put("matter",tags.toString());
        obj.put("total_weight", UnitOfMeasureConversionTable.convert(order.getWeight(), UnitOfMeasure.MEASURES_WEIGHT,order.getWeightUom(),
        UnitOfMeasure.getMeasure(UnitOfMeasure.MEASURES_WEIGHT,"Kgs")));
        obj.put("payment_method","cash");
        JSONArray points = new JSONArray();
        obj.put("points",points);
        points.addAll(makePoints(order));
        ((JSONObject)points.get(0)).put("packages",packages);


        return obj;
    }

    private List<JSONObject> makePoints(Order order) {
        Facility shipFrom = order.getFacility();
        List<OrderAddress> addresses = order.getAddresses();
        OrderAddress shipTo = addresses.stream().filter(a->a.getAddressType().equals(OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get();

        JSONObject from = fillPoint(order,shipFrom) ;

        JSONObject to = fillPoint(order,shipTo);

        to.put("is_order_payment_here",true);
        to.put("delivery_id",1);
        to.put("name",shipTo.getFirstName() + " " + shipTo.getLastName());

        return Arrays.asList(from,to);
    }
    private JSONObject fillPoint(Order order,Address address){
        JSONObject point = new JSONObject();
        StringBuilder buff = new StringBuilder();
        if (!ObjectUtil.isVoid(address.getAddressLine4())){
            buff.append(address.getAddressLine4());
        }else if (!ObjectUtil.isVoid(address.getAddressLine3())){
            buff.append(address.getAddressLine3());
        }else if (!ObjectUtil.isVoid(address.getAddressLine2())){
            buff.append(address.getAddressLine2());
        }else if (!ObjectUtil.isVoid(address.getAddressLine1())){
            buff.append(address.getAddressLine1());
        }
        buff.append(",").append(address.getCity().getName()).append(",").append(address.getState().getName());

        point.put("address",buff.toString());
        JSONObject person = new JSONObject();
        point.put("contact_person", person);
        person.put("phone",address.getPhoneNumber());
        point.put("client_order_id",order.getId());
        point.put("latitude",address.getLat().doubleValue());
        point.put("longitude",address.getLng().doubleValue());


        return point;
    }
}
