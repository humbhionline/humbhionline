package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.math.DoubleHolder;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.CompositeTask;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.integrations.courier.CourierAggregator;
import in.succinct.mandi.integrations.courier.CourierAggregator.CourierOrder;
import in.succinct.mandi.integrations.courier.CourierAggregatorFactory;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.InternalNetwork;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderAttribute;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import in.succinct.plugins.ecommerce.db.model.order.OrderLineAttribute;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrdersController extends in.succinct.plugins.ecommerce.controller.OrdersController {
    public OrdersController(Path path) {
        super(path);
    }

    public View save(long id) {
        Order order = Database.getTable(Order.class).get(id);
        if (order == null){
            return blankJsons();
        }else {
            return super.save();
        }
    }

    public View blankJsons(){
        JSONObject orders = new JSONObject();
        orders.put("Orders",new JSONArray());
        return new BytesView(getPath(),orders.toString().getBytes(StandardCharsets.UTF_8), MimeType.APPLICATION_JSON);
    }
    public View blankJson(){
        JSONObject order = new JSONObject();
        order.put("Order",new JSONObject());
        return new BytesView(getPath(),order.toString().getBytes(StandardCharsets.UTF_8), MimeType.APPLICATION_JSON);
    }
    public View initialize_payment(long id){
        Order order = Database.getTable(Order.class).get(id);
        if (order == null) {
            return blankJson();
        }
        order.initializePayment();
        return show(order);
    }
    public View initialize_refund(long id){
        Order order = Database.getTable(Order.class).get(id);
        if (order == null) {
            return blankJson();
        }
        order.initializeRefund();
        return show(order);
    }
    public View reset_payment(long id){
        Order order = Database.getTable(Order.class).get(id);
        if (order == null) {
            return blankJson();
        }
        order.resetPayment(true);
        return show(order);
    }
    public View reset_refund(long id){
        Order order = Database.getTable(Order.class).get(id);
        if (order == null) {
            return blankJson();
        }
        order.resetRefund(true);
        return show(order);
    }
    public View complete_payment(long orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        if (order == null) {
            return blankJson();
        }
        order.completePayment(true);

        TaskManager.instance().execute(new CompositeTask(getTasksToPrint(orderId).toArray(new Task[]{})));
        return show(order);
    }
    public View complete_refund(long orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        if (order == null) {
            return blankJson();
        }
        order.completeRefund(true);
        return show(order);
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
        List<T> ordersElement = rootHelper.getArrayElements("Orders");
        List<Order> orders = new ArrayList<>();
        for (T orderElement : ordersElement) {
            FormatHelper<T> helper = FormatHelper.instance(orderElement);
            if (isFacilityInCurrentShard(helper)){
                orders.add(bookOrder(helper));
            }
        }
        Map<Class<? extends Model>,List<String>> map =  getIncludedModelFields();
        return IntegrationAdaptor.instance(Order.class,getIntegrationAdaptor().getFormatClass()).createResponse(getPath(),orders,map.get(Order.class),new HashSet<>(),map);
    }
    public <T> boolean isFacilityInCurrentShard(FormatHelper<T> helper){
        T facilityElement = helper.getElementAttribute("Facility");
        Facility facility = ModelIOFactory.getReader(Facility.class,helper.getFormatClass()).read(facilityElement);
        return facility != null && !facility.getRawRecord().isNewRecord();
    }
    public <T> Order bookOrder(FormatHelper<T> helper){

        List<T> orderLinesElement = helper.getArrayElements("OrderLine");
        List<T> orderAddressesElement = helper.getArrayElements("OrderAddress");
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
            Inventory inventory = getInventory(inventoryElement);

            if (!inventory.isPublished()){
                //Trying to make it infinite. Record in db is finite and zero.
                throw new RuntimeException("Product " + inventory.getSku().getName() + " is no longer be available.");
            }else if (inventory.getReflector().isVoid(inventory.getSellingPrice()) && !inventory.isExternal() &&
                    order.getReflector().isVoid(order.getRefOrderId())){
                //It is product order.
                order.setOnHold(true);
                order.setHoldReason(Order.HOLD_REASON_CATALOG_INCOMPLETE);
            }
            if (shipFrom == null){
                shipFrom = inventory.getFacility().getRawRecord().getAsProxy(Facility.class);
            }

            lineHelper.setAttribute("OrderId",StringUtil.valueOf(order.getId()));
            lineHelper.setAttribute("ShipFromId",StringUtil.valueOf(inventory.getFacilityId()));
            lineHelper.setAttribute("SkuId",StringUtil.valueOf(inventory.getSkuId()));
            if (!inventory.getRawRecord().isNewRecord()) {
                lineHelper.setAttribute("InventoryId", StringUtil.valueOf(inventory.getId()));
            }
            lineHelper.removeElementAttribute("Inventory");

            OrderLine line =  ModelIOFactory.getReader(OrderLine.class,lineHelper.getFormatClass()).read(orderLineElement);

            if (AssetCode.getDeliverySkuIds().contains(line.getSkuId()) && inventory.isExternal()){
                CourierAggregator courierAggregator = CourierAggregatorFactory.getInstance().getCourierAggregator(BecknNetwork.find(inventory.getNetworkId()));
                CourierOrder courierOrder = courierAggregator.book(inventory,order,order.getParentOrder());

                double sellingPrice = courierOrder.getOrder().getPayment().getParams().getAmount();
                if  (sellingPrice == 0){
                    sellingPrice = courierOrder.getOrder().getQuote().getPrice().getValue();
                }
                String orderId = courierOrder.getOrder().getId();
                order.setExternalTransactionReference(orderId);
                order.setExternalPlatformId(courierOrder.getContext().getBppId());
                order.setReference("Courier Order Id:" + orderId);

                order.setShippingSellingPrice(sellingPrice);
                order.setShippingPrice(sellingPrice * (1.0 - line.getSku().getTaxRate()/100.0));
                Map<String, OrderAttribute> map = order.getAttributeMap();
                map.get("courier_id").setValue(courierOrder.getContext().getBppId());
                map.get("courier_url").setValue(courierOrder.getContext().getBppUri());
                map.get("external_facility_id").setValue(inventory.getExternalFacilityId());
                map.get("external_order_id").setValue(orderId);
                map.get("network_id").setValue(inventory.getNetworkId());


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
            Sku sku = line.getSku().getRawRecord().getAsProxy(Sku.class);
            Item item = sku.getItem();
            AssetCode assetCode = item.getAssetCode();

            double taxRate = sku.getTaxRate();

           if (ObjectUtil.isVoid(shipFrom.getGSTIN())){
                taxRate = 0.0;
            }
            line.setPrice(new DoubleHolder(line.getSellingPrice()/(1.0 + taxRate/100.0),2).getHeldDouble().doubleValue());


            if (shippingWithinSameState == null) {
                shippingWithinSameState = ObjectUtil.equals(line.getShipFrom().getStateId(), shipTo.getStateId());
            }

            double tax = new DoubleHolder((taxRate/100.0)*line.getPrice(),2).getHeldDouble().doubleValue();
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
            Map<String, OrderLineAttribute> attrMap = line.getAttributeMap();
            attrMap.get("external_sku_id").setValue(inventory.getExternalSkuId());
            line.saveAttributeMap(attrMap);

        }
        if (shippingWithinSameState == null){
            throw new RuntimeException("No Lines passed");
        }

        // Remove lines not passed.
        {
            for (Long id : existingOrderLineMap.keySet()) {
                OrderLine line = existingOrderLineMap.get(id);
                line.destroy();
            }
            existingOrderLineMap.clear();
        }
        if (order.getShippingSellingPrice() == 0 && !order.isCustomerPickup() && order.getRefOrderId() == null){ //Product order.
            order.setShippingSellingPrice(OrderUtil.getDeliveryCharges(shipTo,order.getFacility()));
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
        order.setFacilityId(shipFrom.getId());
        order.save();

        List<User> users = new ArrayList<>();
        for (UserFacility uf : order.getFacility().getFacilityUsers()){
            User user = uf.getUser().getRawRecord().getAsProxy(User.class);
            users.add(user);
        }
        users.addAll(order.getFacility().getOperatingUsers());
        users.addAll(CompanyUtil.getAdminUsers());
        for (User user: users){
            Map<String,Object> entityMap = TemplateEngine.getInstance().createEntityMap(Arrays.asList(order));
            TemplateEngine.getInstance().send(user,"New Order #" +order.getId() + " arrived." , "New_Order.ftlh",entityMap, getIncludedModelFields(),new HashMap<>());
        }
        if (order.getFacility().isCodEnabled()){
            order.getFacility().notifyEvent(Facility.EVENT_TYPE_BOOK_ORDER,order);
        }

        return order;
    }

    public <T> Inventory getInventory(T inventoryElement){
        FormatHelper<T> inventoryHelper = FormatHelper.instance(getPath().getProtocol(),inventoryElement);

        T skuElement = inventoryHelper.getElementAttribute("Sku");

        FormatHelper<T> skuHelper = FormatHelper.instance(skuElement);
        T itemElement = skuHelper.getElementAttribute("Item");
        T uomElement = skuHelper.getElementAttribute("PackagingUOM");
        if (uomElement != null){
            UnitOfMeasure unitOfMeasure = ModelIOFactory.getReader(UnitOfMeasure.class,skuHelper.getFormatClass()).read(uomElement);
            if (unitOfMeasure.getRawRecord().isNewRecord()){
                unitOfMeasure.save();
                FormatHelper.instance(uomElement).setAttribute("Id",String.valueOf(unitOfMeasure.getId()));
            }
        }
        if (itemElement != null){
            Item item = ModelIOFactory.getReader(Item.class,skuHelper.getFormatClass()).read(itemElement);
            if (item.getRawRecord().isNewRecord()){
                item.save();
                FormatHelper.instance(itemElement).setAttribute("Id",String.valueOf(item.getId()));
            }
        }


        if (skuElement != null){
            //sku is new item. !!
            Sku sku = ModelIOFactory.getReader(Sku.class,skuHelper.getFormatClass()).read(skuElement);
            if (sku.getRawRecord().isNewRecord()){
                sku.setPublished(true);
                sku.save();
                skuHelper.setAttribute("Id",String.valueOf(sku.getId()));
            }
        }
        Inventory inventory =  ModelIOFactory.getReader(Inventory.class,inventoryHelper.getFormatClass()).read(inventoryElement);
        if (inventory.getRawRecord().isNewRecord() &&  !ObjectUtil.equals(inventory.isExternal(),true)){
            inventory.setMaxRetailPrice(inventory.getSku().getMaxRetailPrice());
            inventory.setSellingPrice(inventory.getMaxRetailPrice());
            inventory.save();
            inventoryHelper.setAttribute("Id",String.valueOf(inventory.getId()));
        }
        return inventory;
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
        if (ObjectUtil.isVoid(shipTo.getFirstName()) && ObjectUtil.isVoid(shipTo.getLastName()) && ObjectUtil.isVoid(shipTo.getLongName())){
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

    public boolean verifySignature(String payload, String signature){
        User user = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (user.getDevices().isEmpty()){
            throw new RuntimeException("Invalid api call");
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
            signedToVerify.append("JSESSIONID:").append(getPath().getSession().getId()).append(",").append("fms.Token:").append(fmsToken).append(",");
            signedToVerify.append("/orders/processUpi").append("|").append(payload);
            Config.instance().getLogger(getClass().getName()).info("Payload to verify:\n" + signedToVerify);
            verified = Crypt.getInstance().verifySignature(signedToVerify.toString(),Crypt.SIGNATURE_ALGO,signature,
                    Crypt.getInstance().getPublicKey(Crypt.KEY_ALGO,publicKey));
            if (verified){
                break;
            }
        }
        return verified;
    }
    public View processUpi() throws IOException {
        String payload = StringUtil.read(getPath().getInputStream());

        JSONObject upiResponse = (JSONObject)JSONValue.parse(payload);
        String txnRef = (String)upiResponse.get("txnRef");
        String[] orderInfo = txnRef.split(":");
        boolean isRefund = ObjectUtil.equals(orderInfo[0],"R");
        Long orderId = getReflector().getJdbcTypeHelper().getTypeRef(Long.class).getTypeConverter().valueOf(orderInfo[1]);
        if (orderId == null){
            throw new RuntimeException("Cannot Find order " + orderId);
        }
        Order order = Database.getTable(Order.class).lock(orderId);
        if (order == null) {
            return blankJson();
        }

        String signature = getPath().getRequest().getHeader("X-Signature");
        if (ObjectUtil.isVoid(signature) || !verifySignature(payload,signature)){
            throw new RuntimeException("Signature verification failed");
        }

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

    @Override
    protected View show(in.succinct.plugins.ecommerce.db.model.order.Order record) {
        loadCreatorUser(record);
        return super.show(record);
    }

    @Override
    protected <T> View list(List<in.succinct.plugins.ecommerce.db.model.order.Order> records, boolean isCompleteList, IntegrationAdaptor<in.succinct.plugins.ecommerce.db.model.order.Order, T> overrideIntegrationAdaptor) {
        loadCreatorUsers(records);
        return super.list(records, isCompleteList, overrideIntegrationAdaptor);
    }

    public void loadCreatorUser(in.succinct.plugins.ecommerce.db.model.order.Order order){
        loadCreatorUsers(Arrays.asList(order));
    }
    public void loadCreatorUsers(List<in.succinct.plugins.ecommerce.db.model.order.Order> orders){
        Set<Long> creatorUserIds = new HashSet<>();
        orders.forEach(o->{
            creatorUserIds.add(o.getCreatorUserId());
        });

        {
            List<User> users = new Select().from(User.class).where(new Expression(ModelReflector.instance(User.class).getPool(), "ID", Operator.IN, creatorUserIds.toArray())).execute();
            users.forEach(u -> creatorUserIds.remove(u.getId()));
        }

        if (creatorUserIds.isEmpty()){
            return;
        }

        List<ServerNode> nodes = InternalNetwork.getNodes();
        Map<String,String> headers = InternalNetwork.extractHeaders(getPath());
        headers.remove("Cookie");// Do this search with out login! Only using app authenticat

        StringBuilder q = new StringBuilder();
        creatorUserIds.forEach(id->{
            if (q.length() > 0){
                q.append( " OR ");
            }
            q.append("_ID:").append(id);
        });

        for (ServerNode node : nodes){
            if (node.isSelf()){
                continue;
            }

            JSONObject input = new JSONObject();
            input.put("q",q.toString());
            JSONObject aResponse = (JSONObject) new Call<JSONObject>().url(node.getBaseUrl()+"/users/internal_search").input(input).
                    inputFormat(InputFormat.FORM_FIELDS).headers(headers)
                    .method(HttpMethod.GET).getResponseAsJson();
            if (aResponse != null){
                JSONArray users = (JSONArray) aResponse.get("Users");
                for (Object jsonUser: users){
                    InternalNetwork.loadUserToCache((JSONObject) jsonUser);
                }
            }
        }


    }
}
