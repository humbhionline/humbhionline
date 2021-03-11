package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.collections.SequenceSet;
import com.venky.core.date.DateUtils;
import com.venky.core.math.DoubleHolder;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.CompositeTask;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import com.venky.swf.routing.Config;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.RefOrder;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.integrations.courier.Wefast;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.attributes.Attribute;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderAttribute;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import in.succinct.plugins.ecommerce.db.model.order.OrderStatus;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OrdersController extends in.succinct.plugins.ecommerce.controller.OrdersController implements TemplateLoader {
    public OrdersController(Path path) {
        super(path);
    }

    public View initialize_payment(long id){
        Order order = Database.getTable(Order.class).get(id);
        order.initializePayment();
        return show(id);
    }
    public View initialize_refund(long id){
        Order order = Database.getTable(Order.class).get(id);
        order.initializeRefund();
        return show(id);
    }
    public View reset_payment(long id){
        Order order = Database.getTable(Order.class).get(id);
        order.resetPayment(true);
        return show(id);
    }
    public View reset_refund(long id){
        Order order = Database.getTable(Order.class).get(id);
        order.resetRefund(true);
        return show(id);
    }
    public View complete_payment(long orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        order.completePayment(true);

        TaskManager.instance().execute(new CompositeTask(getTasksToPrint(orderId).toArray(new Task[]{})));
        return show(orderId);
    }
    public View complete_refund(long orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        order.completeRefund(true);
        return show(orderId);
    }


    public <T> View book() throws Exception {
        HttpServletRequest request = getPath().getRequest();
        if (!request.getMethod().equalsIgnoreCase("POST")) {
            throw new RuntimeException("Cannot call save in any other method other than POST");
        }
        if (getIntegrationAdaptor() == null) {
            throw new RuntimeException("Can be called only as an xml or json api.");
        }
        FormatHelper<T> rootHelper = FormatHelper.instance(getPath().getProtocol(), request.getInputStream());
        //Orders
        List<T> ordersElement = rootHelper.getChildElements("Orders");
        List<Order> orders = new ArrayList<>();
        for (T orderElement : ordersElement) {
            FormatHelper<T> helper = FormatHelper.instance(orderElement);
            orders.add(bookOrder(helper));
        }
        Map<Class<? extends Model>,List<String>> map =  getIncludedModelFields();
        return IntegrationAdaptor.instance(Order.class,getIntegrationAdaptor().getFormatClass()).createResponse(getPath(),orders,map.get(Order.class),new HashSet<>(),map);
    }
    public <T> Order bookOrder(FormatHelper<T> helper){

        List<T> orderLinesElement = helper.getChildElements("OrderLine");
        List<T> orderAddressesElement = helper.getChildElements("OrderAddress");
        helper.removeAttribute("OrderLines");
        helper.removeAttribute("OrderAddresses");


        //T rootElem  = rootHelper.getRoot();
        Order order = ModelIOFactory.getReader(Order.class,helper.getFormatClass()).read(helper.getRoot());
        Timestamp today = new Timestamp(DateUtils.getStartOfDay(System.currentTimeMillis()));
        if (order.getShipByDate() == null){
            order.setShipByDate(new Timestamp(DateUtils.addHours(today.getTime(),1*24)));
        }

        if (order.getShipAfterDate() == null){
            order.setShipAfterDate(today);
        }

        if (order.getCompanyId() == null){
            order.setCompanyId(CompanyUtil.getCompanyId());
        }
        save(order,Order.class);
        Map<Long, OrderLine> existingOrderLineMap = getOrderLineMap(order);

        OrderAddress shipTo = null;
        OrderAddress billTo = null;
        for (T orderAddressElement :orderAddressesElement){
            FormatHelper<T> addressHelper = FormatHelper.instance(getPath().getProtocol(),orderAddressElement);
            addressHelper.setAttribute("OrderId",StringUtil.valueOf(order.getId()));
            OrderAddress address =  ModelIOFactory.getReader(OrderAddress.class,addressHelper.getFormatClass()).read(orderAddressElement);
            address.save();
            if (ObjectUtil.equals(address.getAddressType(),OrderAddress.ADDRESS_TYPE_SHIP_TO)){
                shipTo = address;
            }else if (ObjectUtil.equals(address.getAddressType(),OrderAddress.ADDRESS_TYPE_BILL_TO)){
                billTo = address;
            }
        }

        validateShipTo(shipTo);

        double defaultGSTPct = 18;
        Boolean shippingWithinSameState = null;
        Cache<String,Bucket> buckets = new Cache<String, Bucket>() {
            @Override
            protected Bucket getValue(String fieldName) {
                return new Bucket();
            }
        };

        String[] LINE_FIELDS_TO_SYNC = new String[] {"PRODUCT_SELLING_PRICE","PRODUCT_PRICE","C_GST", "I_GST", "S_GST"};

        Facility shipFrom = null;
        for (T orderLineElement :orderLinesElement){
            FormatHelper<T> lineHelper = FormatHelper.instance(getPath().getProtocol(),orderLineElement);
            T inventoryElement = lineHelper.getElementAttribute("Inventory");

            FormatHelper<T> inventoryHelper = FormatHelper.instance(getPath().getProtocol(),inventoryElement);
            Inventory inventory  =  ModelIOFactory.getReader(Inventory.class,inventoryHelper.getFormatClass()).read(inventoryElement);
            if (shipFrom == null){
                shipFrom = inventory.getFacility().getRawRecord().getAsProxy(Facility.class);
            }

            lineHelper.setAttribute("OrderId",StringUtil.valueOf(order.getId()));
            lineHelper.setAttribute("ShipFromId",StringUtil.valueOf(inventory.getFacilityId()));
            lineHelper.setAttribute("SkuId",StringUtil.valueOf(inventory.getSkuId()));

            OrderLine line =  ModelIOFactory.getReader(OrderLine.class,lineHelper.getFormatClass()).read(orderLineElement);

            if (AssetCode.getDeliverySkuIds().contains(line.getSkuId()) && ObjectUtil.equals(inventory.getManagedBy(),Inventory.WEFAST)){
                Wefast wefast = new Wefast();
                JSONObject wefastResponse = wefast.createOrder(order,order.getParentOrder());
                double sellingPrice = wefast.getPrice(wefastResponse);
                double discount = wefast.getDiscount(wefastResponse);
                long orderId = wefast.getOrderId(wefastResponse);
                String tracking_url = wefast.getTrackingUrl(wefastResponse);

                order.setReference("Wefast Order Id:" + orderId);
                order.setShippingSellingPrice(sellingPrice);
                order.setShippingPrice(sellingPrice);
                Map<String, OrderAttribute> map = order.getAttributeMap();
                map.get("courier").setValue(Inventory.WEFAST);
                map.get("order_id").setValue(StringUtil.valueOf(orderId));
                if (!ObjectUtil.isVoid(tracking_url)){
                    map.get("tracking_url").setValue(tracking_url);
                }
                order.saveAttributeMap(map);
                line.setSellingPrice(0);
                line.setMaxRetailPrice(0);
            }else {
                line.setSellingPrice(inventory.getSellingPrice() * line.getOrderedQuantity());
                line.setMaxRetailPrice(inventory.getReflector().getJdbcTypeHelper().
                        getTypeRef(double.class).getTypeConverter().valueOf(inventory.getMaxRetailPrice()) * line.getOrderedQuantity());
            }

            if (line.getMaxRetailPrice() == 0){
                line.setMaxRetailPrice(line.getSellingPrice());
            }

            if (line.getMaxRetailPrice() > 0) {
                line.setDiscountPercentage((line.getMaxRetailPrice() - line.getSellingPrice()) / line.getMaxRetailPrice());
            }else {
                line.setDiscountPercentage(0);
            }

            if (!line.getRawRecord().isNewRecord()){
                existingOrderLineMap.remove(line.getId());
            }
            Sku sku = line.getSku();
            Item item = sku.getItem();
            AssetCode assetCode = item.getAssetCode();

            Double taxRate = sku.getTaxRate();

            if (taxRate == null && assetCode != null){
                taxRate = assetCode.getGstPct();
            }
            if (taxRate == null){
                taxRate = defaultGSTPct;
            }
            if (ObjectUtil.isVoid(shipFrom.getGSTIN())){
                taxRate = 0.0;
            }
            line.setPrice(line.getSellingPrice()/(1.0 + taxRate/100.0));


            if (shippingWithinSameState == null) {
                shippingWithinSameState = ObjectUtil.equals(line.getShipFrom().getStateId(), shipTo.getStateId());
            }

            double tax = (taxRate/100.0)*line.getPrice();
            if (shippingWithinSameState){
                line.setCGst(tax/2.0);
                line.setSGst(tax/2.0);
                line.setIGst(0.0);
            }else{
                line.setIGst(tax);
                line.setCGst(0.0);
                line.setSGst(0.0);
            }
            ///Pricing.roundOff(line);
            for (String priceField : LINE_FIELDS_TO_SYNC) {
                buckets.get(priceField).increment(line.getReflector().getJdbcTypeHelper().getTypeRef(double.class).getTypeConverter().valueOf(line.getReflector().get(line,priceField)));
            }

            line.save();
        }
        if (shippingWithinSameState == null){
            throw new RuntimeException("No Lines passed");
        }

        // Remove lines not passed.
        {
            for (Iterator<Long> lineIdIterator = existingOrderLineMap.keySet().iterator(); lineIdIterator.hasNext(); ) {
                Long id = lineIdIterator.next();
                OrderLine line = existingOrderLineMap.get(id);
                line.destroy();
            }
            existingOrderLineMap.clear();
        }

        if (order.getShippingSellingPrice() > 0){
            if (order.getShippingPrice() == 0){
                order.setShippingPrice(new DoubleHolder(order.getShippingSellingPrice()/(1+defaultGSTPct/100.0) , 2).getHeldDouble().doubleValue());
            }
            double shippingTax= order.getShippingSellingPrice() - order.getShippingPrice();
            if (shippingWithinSameState){
                buckets.get("C_GST").increment(shippingTax/2.0);
                buckets.get("S_GST").increment(shippingTax/2.0);
                buckets.get("I_GST").increment(0);
            }else {
                buckets.get("C_GST").increment(0);
                buckets.get("S_GST").increment(0);
                buckets.get("I_GST").increment(shippingTax);
            }
        }


        for (String priceField : LINE_FIELDS_TO_SYNC) {
            order.getReflector().set(order,priceField,buckets.get(priceField).doubleValue());
        }

        order.setSellingPrice(order.getProductSellingPrice() + order.getShippingSellingPrice());
        order.setPrice(order.getProductPrice() + order.getShippingPrice());

        if (order.getReflector().isVoid(order.getReference())){
            order.setReference(""+order.getId());
        }
        if (shipFrom != null) {
            order.setFacilityId(shipFrom.getId());
        }
        order.save();

        List<User> users = new ArrayList<>();
        for (UserFacility uf : order.getFacility().getFacilityUsers()){
            User user = uf.getUser().getRawRecord().getAsProxy(User.class);
            users.add(user);
        }
        users.add(order.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class));
        users.addAll(CompanyUtil.getAdminUsers());
        for (User user: users){
            Map<String,Object> entityMap = TemplateEngine.getInstance().createEntityMap(Arrays.asList(order));
            TemplateEngine.getInstance().send(user,"New Order #" +order.getId() + " arrived." , "New_Order.ftlh",entityMap, getIncludedModelFields(),new HashMap<>());
        }

        return order;
    }



    private Map<Long, OrderLine> getOrderLineMap(Order order) {
        Map<Long,OrderLine> map = new Cache<Long, OrderLine>(0,0) {
            @Override
            protected OrderLine getValue(Long aLong) {
                return null;
            }
        };
        for (OrderLine orderLine: order.getOrderLines()){
            map.put(orderLine.getId(),orderLine);
        }
        return map;
    }

    private void validateShipTo(OrderAddress shipTo) {
        if (shipTo == null){
            throw new RuntimeException("Ship to address not specified");
        }
        /*if (ObjectUtil.isVoid(shipTo.getEmail())){
            throw new RuntimeException("Ship to email not specified");
        }*/
        if (ObjectUtil.isVoid(shipTo.getPhoneNumber())){
            throw new RuntimeException("ShipTo phone number not specified");
        }
        if (ObjectUtil.isVoid(shipTo.getFirstName()) && ObjectUtil.isVoid(shipTo.getLastName())){
            throw new RuntimeException("ShipTo FirstName  and LastName not specified");
        }
    }

    @Override
    protected String[] getIncludedFields() {
        return getIncludedModelFields().get(Order.class).toArray(new String[] {});
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>,List<String>> map= super.getIncludedModelFields();
        map.putAll(Order.getIncludedModelFields());
        map.remove(OrderAddress.class);
        return map;
    }

    public View processUpi() throws IOException {
        String signature = getPath().getRequest().getHeader("X-Signature");
        String payload = StringUtil.read(getPath().getInputStream());
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);

        if (user.getDevices().isEmpty()){
            throw new RuntimeException("Invalid api call");
        }
        if (ObjectUtil.isVoid(signature)){
            throw new RuntimeException("Signature verification failed");
        }
        boolean verified = false;
        for (Device device : user.getDevices()){
            JSONObject keyJson = device.getSubscriptionJson();
            String fmsToken = (String)keyJson.get("token");
            String publicKey = (String)keyJson.get("key");
            String client = (String)keyJson.get("client");
            if (!ObjectUtil.equals(client,"android") || ObjectUtil.isVoid(fmsToken) || ObjectUtil.isVoid(publicKey)){
                continue;
            }
            StringBuilder signedToVerify = new StringBuilder();
            signedToVerify.append("JSESSIONID:"+getPath().getSession().getId()).append(",").append("fms.Token:" +fmsToken).append(",");
            signedToVerify.append("/orders/processUpi").append("|").append(payload);
            Config.instance().getLogger(getClass().getName()).info("Payload to verify:\n" + signedToVerify.toString());
            verified = Crypt.getInstance().verifySignature(signedToVerify.toString(),signature,publicKey);
            if (verified){
                break;
            }
        }
        if (!verified){
            throw new RuntimeException("Signature verification failed.");
        }


        JSONObject upiResponse = (JSONObject)JSONValue.parse(payload);
        String txnRef = (String)upiResponse.get("txnRef");
        String[] orderInfo = txnRef.split(":");
        boolean isRefund = ObjectUtil.equals(orderInfo[0],"R");
        Long orderId = getReflector().getJdbcTypeHelper().getTypeRef(Long.class).getTypeConverter().valueOf(orderInfo[1]);
        if (orderId == null){
            throw new RuntimeException("Cannot Find order " + orderId);
        }
        Order order = Database.getTable(Order.class).lock(orderId);

        boolean success = ObjectUtil.equals(upiResponse.get("Status"),"SUCCESS");
        if (success){
            if (isRefund){
                order.completeRefund(false);
            }else {
                order.completePayment(false);
            }
        }else {
            if (isRefund) {
                order.resetRefund(false);
            }else {
                order.resetPayment(false);
            }
        }
        order.setUpiResponse(upiResponse.toString());
        order.save();

        return show(order);
    }

    public View delivery_plan(long id){
        return new RedirectorView(getPath(),"/dashboard","?search=Delivery&order_id="+id);
    }

}
