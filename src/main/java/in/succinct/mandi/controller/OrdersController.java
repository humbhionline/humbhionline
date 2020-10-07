package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.model.reflection.ModelReflector.FieldMatcher;
import com.venky.swf.db.table.Table.ColumnDescriptor;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.CompositeTask;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import in.succinct.plugins.ecommerce.db.model.order.OrderStatus;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
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
            line.setMaxRetailPrice(line.getSku().getMaxRetailPrice() * line.getOrderedQuantity());
            line.setSellingPrice(inventory.getSellingPrice() *line.getOrderedQuantity());
            line.setDiscountPercentage((line.getMaxRetailPrice()  - line.getSellingPrice())/line.getMaxRetailPrice());

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
            order.setShippingPrice(order.getShippingSellingPrice()/(1+defaultGSTPct));
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
        Map<Class<? extends Model>,List<String>> map =  super.getIncludedModelFields();
        map.put(Order.class,ModelReflector.instance(Order.class).getFields());

        map.put(Facility.class,ModelReflector.instance(Facility.class).getUniqueFields());
        map.get(Facility.class).add("CREATOR_USER_ID");

        ModelReflector<OrderLine> orderLineModelReflector = ModelReflector.instance(OrderLine.class);
        map.put(OrderLine.class,orderLineModelReflector.getFields(cd ->  cd.getName().equals("ID") || ( !cd.getName().equals("ORDER_ID") && !orderLineModelReflector.isHouseKeepingField(orderLineModelReflector.getFieldName(cd.getName())) ) ));

        ModelReflector<OrderAddress> orderAddressModelReflector = ModelReflector.instance(OrderAddress.class);
        map.put(OrderAddress.class, orderAddressModelReflector.getFields(cd -> !cd.getName().equals("ORDER_ID")  &&!orderAddressModelReflector.isHouseKeepingField(orderAddressModelReflector.getFieldName(cd.getName()))));


        ModelReflector<OrderStatus> orderStatusModelReflector = ModelReflector.instance(OrderStatus.class);
        map.put(OrderStatus.class,orderStatusModelReflector.getFields(cd -> !cd.getName().equals("ORDER_ID")  && !orderStatusModelReflector.isHouseKeepingField(orderStatusModelReflector.getFieldName(cd.getName()))));

        ModelReflector<Sku> skuModelReflector = ModelReflector.instance(Sku.class);
        map.put(Sku.class,skuModelReflector.getFields(cd -> !skuModelReflector.isHouseKeepingField(skuModelReflector.getFieldName(cd.getName()))));

        List<String> userFields = new ArrayList<>();
        for (String addressField : Address.getAddressFields()) {
            userFields.add(addressField);
        }
        userFields.addAll(ModelReflector.instance(User.class).getUniqueFields());
        userFields.addAll(Arrays.asList("ID","NAME_AS_IN_BANK_ACCOUNT","VIRTUAL_PAYMENT_ADDRESS"));
        map.put(User.class,userFields);

        return map;
    }

    public View processUpi() throws IOException {
        JSONObject upiResponse = (JSONObject)JSONValue.parse(new InputStreamReader(getPath().getInputStream()));
        String txnRef = (String)upiResponse.get("txnRef");
        String[] orderInfo = txnRef.split(":");
        boolean isRefund = ObjectUtil.equals(orderInfo[0],"R");
        Long orderId = getReflector().getJdbcTypeHelper().getTypeRef(Long.class).getTypeConverter().valueOf(orderInfo[1]);
        if (orderId != null){
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


}
