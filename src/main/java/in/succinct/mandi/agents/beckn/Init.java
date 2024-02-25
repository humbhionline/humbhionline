package in.succinct.mandi.agents.beckn;


import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.math.DoubleHolder;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import in.succinct.beckn.Billing;
import in.succinct.beckn.Context;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnInit;
import in.succinct.beckn.Order;
import in.succinct.beckn.Order.NonUniqueItems;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.mandi.util.beckn.OrderUtil.OrderFormat;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderAttribute;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.sql.Timestamp;
import java.util.Map;

public class Init extends BecknAsyncTask {

    public Init(Request request , Map<String,String> headers){
        super(request,headers);
    }
    @Override
    public Request executeInternal() {
        Request request = getRequest();
        Context context = request.getContext();
        Order becknOrder = request.getMessage().getOrder();
        //Provider provider = becknOrder.getProvider();

        in.succinct.mandi.db.model.Order order = in.succinct.mandi.db.model.Order.find(context.getTransactionId());
        if (order != null){
            if (order.isOnHold() && ObjectUtil.equals(order.getFulfillmentStatus(), in.succinct.mandi.db.model.Order.FULFILLMENT_STATUS_DOWNLOADED)){
                order.setOnHold(false);
                order.setExternalTransactionReference(null);
                order.save();
                order.cancel("Cart Re Initialized");
                order = null;
            }else {
                throw new RuntimeException("Order already confirmed!");
            }
        }
        /* /select change!! jul 7
        long providerUserId = Long.parseLong(BecknUtil.getLocalUniqueId(provider.getId(), Entity.provider));
        User seller = Database.getTable(User.class).get(providerUserId);
        Facility facility = OrderUtil.getOrderFacility(seller,provider.getLocations());
        */
        Facility facility = null;
        {
            long invId = Long.valueOf(BecknUtil.getLocalUniqueId(becknOrder.getItems().get(0).getId(), Entity.item));
            Inventory inventory = invId > 0 ? Database.getTable(Inventory.class).get(invId) : null ;
            if (inventory == null){
                OnInit onInit = new OnInit();
                onInit.setContext(context);
                onInit.getContext().setAction("on_init");
                onInit.setMessage( new Message()); //Empty
                return onInit;
            }
            facility = inventory.getFacility().getRawRecord().getAsProxy(Facility.class);
        }

        order = Database.getTable(in.succinct.mandi.db.model.Order.class).newRecord();
        order.setFacilityId(facility.getId());
        order.setCompanyId(CompanyUtil.getCompanyId());
        if (facility.isDeliveryProvided()) {
            order.setShippingSellingPrice(OrderUtil.getDeliveryCharges(OrderUtil.getAddress(becknOrder.getFulfillment().getEnd().getLocation()), facility));
        }
        long today = DateUtils.getStartOfDay(System.currentTimeMillis());
        if (order.getShipByDate() == null){
            order.setShipByDate(new Timestamp(DateUtils.addHours(today,24)));
        }
        if (order.getShipAfterDate() == null){
            order.setShipAfterDate(new Timestamp(today));
        }
        order.setExternalTransactionReference(context.getTransactionId());
        order.setExternalPlatformId(context.getBapId());
        order.save();
        Map<String, OrderAttribute> map = order.getAttributeMap();
        map.get("external_platform_url").setValue(context.getBapUri());
        map.get("external_platform_id").setValue(context.getBapId());
        map.get("self_platform_url").setValue(context.getBppUri());
        map.get("self_platform_id").setValue(context.getBppId());
        map.get("domain").setValue(context.getDomain());

        order.saveAttributeMap(map);

        Fulfillment fulfillment = becknOrder.getFulfillment();
        Billing billing = becknOrder.getBilling();
        OrderAddress shipTo = createShipTo(order,fulfillment);
        OrderAddress billTo = createBillTo(order,billing);

        NonUniqueItems items = becknOrder.getItems();
        Cache<String,Bucket> buckets = new Cache<String, Bucket>() {
            @Override
            protected Bucket getValue(String fieldName) {
                return new Bucket();
            }
        };
        boolean shippingWithinSameState = ObjectUtil.equals(facility.getStateId(),shipTo.getStateId());

        createOrderLines(items,order,shipTo,buckets);
        if (order.getShippingSellingPrice() > 0){
            if (order.getShippingPrice() == 0){
                double gstPct = ObjectUtil.isVoid(facility.getGSTIN()) ? 0 :  DEFAULT_GST_PCT;
                order.setShippingPrice(new DoubleHolder(order.getShippingSellingPrice()/(1+ gstPct /100.0) , 2).getHeldDouble().doubleValue());
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
        order.setOnHold(true);
        User user = ensureUser(shipTo);
        order.setCreatorUserId(user.getId());
        order.save();

        becknOrder = OrderUtil.toBeckn(order, OrderFormat.initialized);


        OnInit onInit = new OnInit();
        onInit.setContext(context);
        onInit.getContext().setAction("on_init");
        onInit.setMessage(new Message());
        onInit.getMessage().setInitialized(becknOrder);
        return(onInit);

    }

    protected OrderAddress createBillTo(in.succinct.mandi.db.model.Order order, Billing billing) {
        Address address = OrderUtil.getAddress(billing.getAddress());
        OrderAddress orderAddress = Database.getTable(OrderAddress.class).newRecord();
        loadAddress(orderAddress,address);
        if (billing.getName() != null) {
            orderAddress.setFirstName(billing.getName());
        }
        if (billing.getEmail() != null) {
            orderAddress.setEmail(billing.getEmail());
        }
        if (billing.getPhone() != null) {
            orderAddress.setPhoneNumber(billing.getPhone());
        }
        orderAddress.setOrderId(order.getId());
        orderAddress.setAddressType(in.succinct.plugins.ecommerce.db.model.order.OrderAddress.ADDRESS_TYPE_BILL_TO);
        orderAddress = Database.getTable(OrderAddress.class).getRefreshed(orderAddress); // To support update
        orderAddress.save();
        return orderAddress;
    }

    protected OrderAddress createShipTo(in.succinct.mandi.db.model.Order order, Fulfillment fulfillment) {
        FulfillmentStop end = fulfillment.getEnd();
        Address address = OrderUtil.getAddress(end.getLocation());
        OrderAddress orderAddress = Database.getTable(OrderAddress.class).newRecord();
        loadAddress(orderAddress,address);
        OrderUtil.setName(orderAddress,fulfillment.getCustomer().getPerson().getName());
        orderAddress.setOrderId(order.getId());
        orderAddress.setAddressType(in.succinct.plugins.ecommerce.db.model.order.OrderAddress.ADDRESS_TYPE_SHIP_TO);
        orderAddress.setPhoneNumber(end.getContact().getPhone());
        orderAddress.setEmail(end.getContact().getEmail());
        orderAddress.setLat(address.getLat());
        orderAddress.setLng(address.getLng());
        orderAddress = Database.getTable(OrderAddress.class).getRefreshed(orderAddress);// To Support modification;
        orderAddress.save();
        return orderAddress;
    }

    protected User ensureUser(OrderAddress orderAddress) {
        User user = Database.getTable(User.class).newRecord();
        user.setPhoneNumber(Phone.sanitizePhoneNumber(orderAddress.getPhoneNumber()));
        user = Database.getTable(User.class).getRefreshed(user);
        Address.copy(orderAddress,user);
        user.setLongName(orderAddress.getFirstName() + " " + orderAddress.getLastName());
        user.save();
        return user;
    }

    protected void loadAddress(Address to, Address from) {
        to.setAddressLine1(from.getAddressLine1());
        to.setAddressLine2(from.getAddressLine2());
        to.setAddressLine3(from.getAddressLine3());
        to.setAddressLine4(from.getAddressLine4());
        to.setCityId(from.getCityId());
        to.setStateId(from.getStateId());
        to.setPhoneNumber(from.getPhoneNumber());
        to.setEmail(from.getEmail());
        to.setCountryId(from.getCountryId());
        to.setPinCodeId(from.getPinCodeId());
    }

    private static final double DEFAULT_GST_PCT = 18.0;
    private static final String[] LINE_FIELDS_TO_SYNC = new String[] {"PRODUCT_SELLING_PRICE","PRODUCT_PRICE","C_GST", "I_GST", "S_GST"};

    private void createOrderLines(NonUniqueItems items, in.succinct.mandi.db.model.Order order, OrderAddress shipTo, Map<String,Bucket> buckets ) {

        Boolean shippingWithinSameState = null;
        Facility facility = order.getFacility();

        for (Item item : items){
            long invId = Long.parseLong(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
            Quantity quantity = item.get(Quantity.class,"quantity");

            Inventory inventory = Database.getTable(Inventory.class).get(invId);
            OrderLine orderLine = Database.getTable(OrderLine.class).newRecord();
            orderLine.setOrderId(order.getId());
            orderLine.setShipFromId(inventory.getFacilityId());
            orderLine.setSkuId(inventory.getSkuId());
            orderLine.setInventoryId(invId);
            orderLine.setOrderedQuantity(quantity.getCount());
            orderLine.setSellingPrice(inventory.getSellingPrice() * quantity.getCount());
            orderLine.setMaxRetailPrice(inventory.getMaxRetailPrice() * quantity.getCount());
            if (orderLine.getMaxRetailPrice() == 0){
                orderLine.setMaxRetailPrice(orderLine.getSellingPrice());
            }

            if (orderLine.getMaxRetailPrice() > 0) {
                orderLine.setDiscountPercentage((orderLine.getMaxRetailPrice() - orderLine.getSellingPrice()) / orderLine.getMaxRetailPrice());
            }else {
                orderLine.setDiscountPercentage(0);
            }

            Sku sku = orderLine.getSku();
            AssetCode assetCode = sku.getItem().getAssetCode();

            double taxRate = sku.getTaxRate();

            if (taxRate <= 0.0 && assetCode != null){
                taxRate = assetCode.getGstPct();
            }
            if (taxRate <= 0){
                taxRate = DEFAULT_GST_PCT;
            }
            if (ObjectUtil.isVoid(facility.getGSTIN())){
                taxRate = 0.0;
            }
            orderLine.setPrice(new DoubleHolder(orderLine.getSellingPrice()/(1.0 + taxRate/100.0),2).getHeldDouble().doubleValue());


            if (shippingWithinSameState == null) {
                shippingWithinSameState = ObjectUtil.equals(orderLine.getShipFrom().getStateId(), shipTo.getStateId());
            }

            double tax = new DoubleHolder((taxRate/100.0)*orderLine.getPrice(),2).getHeldDouble().doubleValue();
            if (shippingWithinSameState){
                orderLine.setCGst(tax/2.0);
                orderLine.setSGst(tax/2.0);
                orderLine.setIGst(0.0);
            }else{
                orderLine.setIGst(tax);
                orderLine.setCGst(0.0);
                orderLine.setSGst(0.0);
            }
            ///Pricing.roundOff(line);
            for (String priceField : LINE_FIELDS_TO_SYNC) {
                buckets.get(priceField).increment(orderLine.getReflector().getJdbcTypeHelper().getTypeRef(double.class).getTypeConverter().
                        valueOf(orderLine.getReflector().get(orderLine,priceField)));
            }
            orderLine.save();
        }

    }


}
