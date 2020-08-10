package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.SingleRecordAction;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;

import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OrdersController extends in.succinct.plugins.ecommerce.controller.OrdersController implements TemplateLoader {
    public OrdersController(Path path) {
        super(path);
    }


    @SingleRecordAction(icon = "glyphicon-envelope",tooltip = "Deliver")
    public View deliver(long orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        order.deliver();
        if (getIntegrationAdaptor() != null) {
            return getIntegrationAdaptor().createResponse(getPath(), order,null,getIgnoredParentModels(), getIncludedModelFields());
        }else {
            return back();
        }
    }

    @SingleRecordAction(icon="glyphicon-thumbs-down",tooltip="Back order")
    public View backorder(long  orderId){
        Order order = Database.getTable(Order.class).get(orderId);
        order.backorder();
        if (getIntegrationAdaptor() != null) {
            return getIntegrationAdaptor().createResponse(getPath(), order,null,getIgnoredParentModels(), getIncludedModelFields());
        }else {
            return back();
        }
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
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>,List<String>> map =  super.getIncludedModelFields();
        map.put(OrderLine.class,ModelReflector.instance(OrderLine.class).getFields());
        map.put(OrderAddress.class,ModelReflector.instance(OrderAddress.class).getFields());
        map.put(Sku.class,ModelReflector.instance(Sku.class).getFields());

        return map;
    }


}
