package in.succinct.mandi.integrations.courier;

import com.venky.cache.Cache;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

class Wefast implements Courier{

    private static final Cache<JSONObject, JSONObject> pricingApiResponse = new Cache<JSONObject, JSONObject>(100,0.2) {
        @Override
        protected JSONObject getValue(JSONObject orderJson) {
            Call<JSONObject> call = new Call<JSONObject>().url(Config.instance().getProperty("wefast.api.url") ,
                    "/calculate-order").
                    header("X-DV-Auth-Token",Config.instance().getProperty("wefast.api.token")).
                    inputFormat(InputFormat.JSON).input(orderJson).method(HttpMethod.POST);

            if (call.hasErrors()){
                throw new RuntimeException(call.getError());
            }

            return call.getResponseAsJson();
        }
    };
    private JSONObject createOrder(Facility transportFacility, Order order){
        JSONObject orderJson = makeJson(order);

        Call<JSONObject> call = new Call<JSONObject>().url(Config.instance().getProperty("wefast.api.url") ,
                "/create-order").
                header("X-DV-Auth-Token",getApiToken(transportFacility)).
                inputFormat(InputFormat.JSON).input(orderJson).method(HttpMethod.POST);

        if (call.hasErrors()){
            throw new RuntimeException(call.getError());
        }

        return call.getResponseAsJson();
    }

    private String getApiToken(Facility transportFacility){
        Inventory rule = transportFacility.getDeliveryRule(true);
        if (rule == null || ObjectUtil.isVoid(rule.getApiToken())){
            throw new RuntimeException(transportFacility.getName() + " does not have integration with wefast");
        }
        return rule.getApiToken();
    }



    private JSONObject getPrice(Order order){
        return pricingApiResponse.get(makeJson(order));
    }
    private JSONObject getPrice(Address from, Address to, Inventory inventory){
        return pricingApiResponse.get(makeJson(from,to,inventory));
    }
    private JSONObject getPrice(Address from, Address to, List<Inventory> inventories){
        return pricingApiResponse.get(makeJson(from,to,inventories));
    }
    private JSONObject makeJson(Address from, Address to, Inventory inventory){
        return makeJson(from,to,Arrays.asList(inventory));
    }
    private JSONObject makeJson(Address from, Address to, List<Inventory> inventories) {
        JSONObject obj = new JSONObject();
        Set<String> tags = new HashSet<>();
        Bucket totalWeight  = new Bucket();
        for (Inventory inventory : inventories ){
            String stags = inventory.getTags();
            if (!ObjectUtil.isVoid(stags)){
                StringTokenizer tok = new StringTokenizer(StringUtil.valueOf(stags));
                while (tok.hasMoreTokens()){
                    tags.add(tok.nextToken());
                }
            }else {
                tags.add(inventory.getSku().getName());
            }
            double weight = 5.0 ;
            UnitOfMeasure weightUom = null;
            if (inventory.getSku().getWeight() != null){
                weight = inventory.getSku().getWeight();
                weightUom = inventory.getSku().getWeightUOM();
            }else {
                UnitOfMeasure uom = inventory.getSku().getPackagingUOM();
                if (uom.getName().endsWith("Kg")){
                    weight = Double.valueOf(uom.getName().replaceAll("Kg$","").trim());
                }else if (uom.getName().endsWith("gms")){
                    weight = Double.valueOf(uom.getName().replaceAll("gms$","").trim())/1000.0D;
                }
                weightUom = UnitOfMeasure.getWeightMeasure("Kgs");
            }
            totalWeight.increment(UnitOfMeasureConversionTable.convert(weight, UnitOfMeasure.MEASURES_WEIGHT,weightUom,
                    UnitOfMeasure.getMeasure(UnitOfMeasure.MEASURES_WEIGHT,"Kgs")));
        }

        obj.put("matter",tags.toString());
        obj.put("total_weight_kg", totalWeight.doubleValue());
        JSONArray points = new JSONArray();
        obj.put("points",points);
        points.addAll(makePoints(from,to));

        return obj;
    }
    private JSONObject makeJson(Order order) {
        JSONObject obj = new JSONObject();
        Set<String> tags = new HashSet<>();
        Bucket weight = new Bucket();
        for (OrderLine line: order.getOrderLines()){
            String stags = line.getInventory().getRawRecord().getAsProxy(Inventory.class).getTags();
            if (!ObjectUtil.isVoid(stags)){
                StringTokenizer tok = new StringTokenizer(StringUtil.valueOf(stags));
                while (tok.hasMoreTokens()){
                    tags.add(tok.nextToken());
                }
            }else if(!ObjectUtil.isVoid(line.getHsn())){
                tags.add(line.getHsn());
            }else {
                tags.add(line.getSku().getName());
            }
        }

        obj.put("matter",tags.toString());
        obj.put("total_weight_kg", UnitOfMeasureConversionTable.convert(order.getWeight(), UnitOfMeasure.MEASURES_WEIGHT,order.getWeightUom(),
        UnitOfMeasure.getMeasure(UnitOfMeasure.MEASURES_WEIGHT,"Kgs")));
        JSONArray points = new JSONArray();
        obj.put("points",points);
        points.addAll(makePoints(order));

        /*
        JSONArray packages = new JSONArray();

        JSONObject p = new JSONObject();
        p.put("ware_code",order.getFacility().getName());
        p.put("description", "HumBhiOnline Order# " + order.getId());
        p.put("items_count", order.getOrderLines().size());
        packages.add(p);
        ((JSONObject)points.get(0)).put("packages",packages);
        */

        return obj;
    }

    private List<JSONObject> makePoints(Order order) {
        Facility shipFrom = order.getFacility();
        List<OrderAddress> addresses = order.getAddresses();
        OrderAddress shipTo = addresses.stream().filter(a->a.getAddressType().equals(OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get();

        JSONObject from = fillPoint(order,shipFrom) ;
        JSONObject to = fillPoint(order,shipTo);

        //to.put("is_order_payment_here",true);
        //to.put("delivery_id",order.getId());
        ((JSONObject)to.get("contact_person")).put("name",shipTo.getFirstName() + " " + shipTo.getLastName());

        return Arrays.asList(from,to);
    }
    private List<JSONObject> makePoints(Address fromloc, Address toloc) {
        JSONObject from = fillPoint(fromloc) ;
        JSONObject to = fillPoint(toloc);
        return Arrays.asList(from,to);
    }
    private JSONObject fillPoint(Address address){
        JSONObject point = new JSONObject();
        StringBuilder buff = new StringBuilder();
        if (!ObjectUtil.isVoid(address.getAddressLine1())){
            buff.append(address.getAddressLine1());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine2())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine2());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine3())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine3());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine4())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine4());
        }
        buff.append(",").append(address.getCity().getName()).append(",").append(address.getState().getName());

        point.put("address",buff.toString());
        JSONObject person = new JSONObject();
        point.put("contact_person", person);
        person.put("phone",address.getPhoneNumber());
        point.put("latitude",address.getLat().doubleValue());
        point.put("longitude",address.getLng().doubleValue());


        return point;

    }
    private JSONObject fillPoint(Order order,Address address){
        JSONObject point = new JSONObject();
        StringBuilder buff = new StringBuilder();
        if (!ObjectUtil.isVoid(address.getAddressLine1())){
            buff.append(address.getAddressLine1());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine2())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine2());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine3())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine3());
        }
        if (!ObjectUtil.isVoid(address.getAddressLine4())){
            if (buff.length() > 0){
                buff.append(", ");
            }
            buff.append(address.getAddressLine4());
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
    private boolean isSuccessful(JSONObject response){
        Boolean success = response.containsKey("is_successful")? (Boolean)response.get("is_successful") : response.get("event_type") != null;
        if (success && response.containsKey("warnings")){
            JSONArray warnings = (JSONArray) response.get("warnings");
            if (warnings.size() >0 ){
                success = !warnings.stream().filter(w->w.equals("invalid_parameters")).findAny().isPresent();
            }
        }
        return success;

    }

    private double getPrice(JSONObject response){
        if (isSuccessful(response)){
            JSONObject jsonResponseOrder = (JSONObject) response.get("order");
            return Database.getJdbcTypeHelper("").getTypeRef(Double.class).getTypeConverter().valueOf(jsonResponseOrder.get("payment_amount"));
        }
        return Double.POSITIVE_INFINITY;
    }
    private double getDiscount(JSONObject response){
        if (isSuccessful(response)){
            JSONObject jsonResponseOrder = (JSONObject) response.get("order");
            return Database.getJdbcTypeHelper("").getTypeRef(Double.class).getTypeConverter().valueOf(jsonResponseOrder.get("discount_amount"));
        }
        return Double.POSITIVE_INFINITY;
    }

    private Long getOrderId(JSONObject response) {
        if (isSuccessful(response)){
            JSONObject jsonResponseOrder = (JSONObject) response.get("order");
            return Database.getJdbcTypeHelper("").getTypeRef(Long.class).getTypeConverter().valueOf(jsonResponseOrder.get("order_id"));
        }
        return 0L;
    }

    private String getTrackingUrl(JSONObject response) {
        String trackingUrl = null;
        if (isSuccessful(response)){
            JSONObject jsonResponseOrder = (JSONObject) response.get("order");
            JSONArray points = (JSONArray) jsonResponseOrder.get("points");
            for (int i = 0 ; i < points.size() && trackingUrl == null; i ++){
                trackingUrl = Database.getInstance().getJdbcTypeHelper("").getTypeRef(String.class).getTypeConverter().
                        valueOf(((JSONObject)points.get(i)).get("tracking_url"));
            }
        }
        return trackingUrl;
    }

    @Override
    public Quote getQuote(Address from, Address to, Inventory inventory) {
        final JSONObject response = getPrice(from,to,inventory);
        return new Quote() {
            @Override
            public CourierDescriptor getCourierDescriptor() {
                return courierDescriptor;
            }

            @Override
            public double getSellingPrice() {
                return Wefast.this.getPrice(response);
            }
        };
    }
    CourierDescriptor courierDescriptor = new CourierDescriptor() {
        @Override
        public String getName() {
            return Inventory.WEFAST;
        }

        @Override
        public String getId() {
            return Inventory.WEFAST;
        }

        @Override
        public String getLogoUrl() {
            return "https://borzodelivery.com/img/global/logo-mobile.svg";
        }

    };

    @Override
    public Quote getQuote(Order retailOrder) {
        return new Quote() {
            @Override
            public CourierDescriptor getCourierDescriptor() {
                return courierDescriptor;
            }

            @Override
            public double getSellingPrice() {
                return Wefast.this.getPrice(Wefast.this.getPrice(retailOrder));
            }
        };
    }

    @Override
    public CourierOrder book(final Order transportOrder, final Order retailOrder) {
        final JSONObject response = Wefast.this.createOrder(transportOrder.getFacility(),retailOrder);
        return getCourierOrder(response);
    }

    public CourierOrder getCourierOrder(JSONObject statusJson){
        return new CourierOrder() {
            @Override
            public CourierDescriptor getCourierDescriptor() {
                return courierDescriptor;
            }

            @Override
            public String getTrackingUrl() {
                return Wefast.this.getTrackingUrl(statusJson);
            }

            @Override
            public String getOrderNumber() {
                return String.valueOf(Wefast.this.getOrderId(statusJson));
            }

            @Override
            public boolean isCompleted() {
                JSONObject order = (JSONObject) statusJson.get("order");
                return  ObjectUtil.equals(order.get("status"),"completed");
            }

            @Override
            public double getSellingPrice() {
                return Wefast.this.getPrice(statusJson);
            }
        };
    }
    public Order getOrder(JSONObject statusJson) {
        long productOrderId = Wefast.this.getOrderId(statusJson);
        return Database.getTable(Order.class).get(productOrderId);
    }
}
