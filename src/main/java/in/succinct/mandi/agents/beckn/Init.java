package in.succinct.mandi.agents.beckn;


import com.venky.cache.Cache;
import com.venky.core.date.DateUtils;
import com.venky.core.math.DoubleHolder;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.PinCode;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import in.succinct.beckn.Billing;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Order;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class Init extends BecknAsyncTask {

    public Init(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        Order becknOrder = request.getMessage().getOrder();
        Provider provider = becknOrder.getProvider();

        Long providerUserId = Long.valueOf(BecknUtil.getLocalUniqueId(provider.getId(), Entity.provider));
        User seller = Database.getTable(User.class).get(providerUserId);
        Facility facility = getOrderFacility(seller,provider.getLocations());

        in.succinct.mandi.db.model.Order order = Database.getTable(in.succinct.mandi.db.model.Order.class).newRecord();
        order.setFacilityId(facility.getId());
        order.setCompanyId(CompanyUtil.getCompanyId());
        order.setShippingSellingPrice(getDeliveryCharges(becknOrder,facility));
        long today = DateUtils.getStartOfDay(System.currentTimeMillis());
        if (order.getShipByDate() == null){
            order.setShipByDate(new Timestamp(DateUtils.addHours(today,1*24)));
        }
        if (order.getShipAfterDate() == null){
            order.setShipAfterDate(new Timestamp(today));
        }
        order.save();
        Fulfillment fulfillment = becknOrder.getFulfillment();
        Billing billing = becknOrder.getBilling();
        OrderAddress shipTo = createShipTo(order,fulfillment.getEnd());
        OrderAddress billTo = createBillTo(order,billing);

        Items items = becknOrder.getItems();
        Cache<String,Bucket> buckets = new Cache<String, Bucket>() {
            @Override
            protected Bucket getValue(String fieldName) {
                return new Bucket();
            }
        };
        boolean shippingWithinSameState = true;
        shippingWithinSameState = ObjectUtil.equals(facility.getStateId(),shipTo.getStateId());

        createOrderLines(items,order,shipTo,buckets);
        if (order.getShippingSellingPrice() > 0){
            if (order.getShippingPrice() == 0){
                order.setShippingPrice(new DoubleHolder(order.getShippingSellingPrice()/(1+ DEFAULT_GST_PCT /100.0) , 2).getHeldDouble().doubleValue());
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
        order.save();
    }

    private OrderAddress createBillTo(in.succinct.mandi.db.model.Order order, Billing billing) {
        Address address = getAddress(billing.getLocation());
        OrderAddress orderAddress = Database.getTable(OrderAddress.class).newRecord();
        loadAddress(orderAddress,address);
        orderAddress.setFirstName(billing.getLocation().getAddress().getName());
        orderAddress.setOrderId(order.getId());
        orderAddress.setAddressType(in.succinct.plugins.ecommerce.db.model.order.OrderAddress.ADDRESS_TYPE_BILL_TO);
        orderAddress.save();
        return orderAddress;
    }

    private OrderAddress createShipTo(in.succinct.mandi.db.model.Order order, FulfillmentStop end) {
        Address address = getAddress(end.getLocation());
        OrderAddress orderAddress = Database.getTable(OrderAddress.class).newRecord();
        loadAddress(orderAddress,address);
        orderAddress.setFirstName(end.getLocation().getAddress().getName());
        orderAddress.setOrderId(order.getId());
        orderAddress.setAddressType(in.succinct.plugins.ecommerce.db.model.order.OrderAddress.ADDRESS_TYPE_SHIP_TO);
        orderAddress.setPhoneNumber(end.getContact().getPhone().getValue());
        orderAddress.setEmail(end.getContact().getEmail().getValue());
        orderAddress.save();
        return orderAddress;
    }

    private void loadAddress(OrderAddress orderAddress, Address address) {
        orderAddress.setAddressLine1(address.getAddressLine1());
        orderAddress.setAddressLine2(address.getAddressLine2());
        orderAddress.setAddressLine3(address.getAddressLine3());
        orderAddress.setAddressLine4(address.getAddressLine4());
        orderAddress.setCityId(address.getCityId());
        orderAddress.setStateId(address.getStateId());
        orderAddress.setPhoneNumber(address.getPhoneNumber());
        orderAddress.setEmail(address.getEmail());
        orderAddress.setCountryId(address.getCountryId());
        orderAddress.setPinCodeId(address.getPinCodeId());
    }

    private static final double DEFAULT_GST_PCT = 18.0;
    private static final String[] LINE_FIELDS_TO_SYNC = new String[] {"PRODUCT_SELLING_PRICE","PRODUCT_PRICE","C_GST", "I_GST", "S_GST"};

    private void createOrderLines(Items items, in.succinct.mandi.db.model.Order order, OrderAddress shipTo, Map<String,Bucket> buckets ) {

        Boolean shippingWithinSameState = null;
        Facility facility = order.getFacility();

        for (Item item : items){
            Long skuId = Long.valueOf(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
            Quantity quantity = item.get(Quantity.class,"quantity");

            Inventory inventory = in.succinct.plugins.ecommerce.db.model.inventory.Inventory.find(facility.getId(),skuId).getRawRecord()
                    .getAsProxy(Inventory.class);
            OrderLine orderLine = Database.getTable(OrderLine.class).newRecord();
            orderLine.setOrderId(order.getId());
            orderLine.setShipFromId(inventory.getFacilityId());
            orderLine.setSkuId(inventory.getSkuId());
            orderLine.setOrderedQuantity(quantity.getMeasure());
            orderLine.setSellingPrice(inventory.getSellingPrice() * quantity.getMeasure());
            orderLine.setMaxRetailPrice(inventory.getMaxRetailPrice() * quantity.getMeasure());
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

            Double taxRate = sku.getTaxRate();

            if (taxRate == null && assetCode != null){
                taxRate = assetCode.getGstPct();
            }
            if (taxRate == null){
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

    public double getDeliveryCharges(Order becknOrder, Facility facility){
        Fulfillment fulfillment = becknOrder.getFulfillment();
        Bucket deliveryCharges = new Bucket();
        if (fulfillment != null){
            if (fulfillment.getType() == FulfillmentType.home_delivery){
                FulfillmentStop end = fulfillment.getEnd();
                facility.setDistance(new GeoCoordinate(facility).distanceTo(end.getLocation().getGps()));
                if (facility.isDeliveryProvided() && facility.getDistance() <= facility.getDeliveryRadius()){
                    Inventory deliveryRule
                            = facility.getDeliveryRule(false);
                    if (deliveryRule == null || ObjectUtil.isVoid(deliveryRule.getManagedBy())){
                        deliveryCharges.increment(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance()),2).getHeldDouble().doubleValue());
                    }else {
                        throw new RuntimeException("Unknown courier:" + deliveryRule.getManagedBy());
                    }
                }
            }
        }
        return deliveryCharges.doubleValue();
    }

    private Facility getOrderFacility(User seller, Locations locations) {
        Location providerLocation = locations.size() > 0 ? locations.get(0) : null;

        Long facilityId = providerLocation != null ? Long.valueOf(BecknUtil.getLocalUniqueId(providerLocation.getId(), Entity.provider_location)) : null;
        if (facilityId == null){
            List<Long> operatingFacilityids =  seller.getOperatingFacilityIds();
            if (!operatingFacilityids.isEmpty()){
                facilityId = operatingFacilityids.get(0);
            }else {
                throw new RuntimeException("Don't know what facility the order was made into!");
            }
        }
        return Database.getTable(Facility.class).get(facilityId);

    }

    public Address getAddress(final Location location){

        return new Address() {
            //Location location = stop.getLocation();
            in.succinct.beckn.Address address = location.getAddress();
            @Override
            public String getAddressLine1() {
                return address.getName() + " " + address.getDoor() + " " + address.getBuilding();
            }

            @Override
            public void setAddressLine1(String line1) {
                return;
            }

            @Override
            public String getAddressLine2() {
                return address.getStreet() +  " " + address.getLocality();
            }

            @Override
            public void setAddressLine2(String line2) {

            }

            @Override
            public String getAddressLine3() {
                return "" ;
            }

            @Override
            public void setAddressLine3(String line3) {

            }

            @Override
            public String getAddressLine4() {
                return "";
            }

            @Override
            public void setAddressLine4(String line4) {

            }

            @Override
            public Long getCityId() {
                return getCity().getId();
            }

            @Override
            public void setCityId(Long cityId) {

            }

            City city = null;

            @Override
            public City getCity() {
                if (city == null) {
                    city = City.findByCountryAndStateAndName(address.getCountry(), address.getState(), address.getCity());
                }
                return city;
            }

            State state = null;
            @Override
            public Long getStateId() {
                return state.getId();
            }

            @Override
            public void setStateId(Long stateId) {

            }

            @Override
            public State getState() {
                if (state == null){
                    state = State.findByCountryAndName(address.getCountry(),address.getState());
                }
                return state;
            }

            @Override
            public Long getCountryId() {
                return null;
            }

            @Override
            public void setCountryId(Long countryId) {

            }

            Country country = null;
            @Override
            public Country getCountry() {
                if (country == null){
                    country = Country.findByISO(address.getCountry());
                }
                return country;
            }

            @Override
            public Long getPinCodeId() {
                return getPinCode().getId();
            }

            @Override
            public void setPinCodeId(Long pincodeId) {

            }

            PinCode pinCode = null;
            @Override
            public PinCode getPinCode() {
                if (pinCode == null){
                    pinCode = PinCode.find(address.getPinCode());
                }
                return pinCode;
            }

            @Override
            public String getEmail() {
                return "";
            }

            @Override
            public void setEmail(String emailId) {

            }

            @Override
            public String getPhoneNumber() {
                return "";
            }

            @Override
            public void setPhoneNumber(String phoneNumber) {

            }

            @Override
            public String getAlternatePhoneNumber() {
                return "";
            }

            @Override
            public void setAlternatePhoneNumber(String phoneNumber) {

            }

            @Override
            public BigDecimal getLat() {
                return location.getGps().getLat();
            }

            @Override
            public void setLat(BigDecimal latitude) {

            }

            @Override
            public BigDecimal getLng() {
                return location.getGps().getLng();
            }

            @Override
            public void setLng(BigDecimal longitude) {

            }
        };
    }
}
