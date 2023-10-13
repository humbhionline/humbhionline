package in.succinct.mandi.util.beckn;

import com.venky.cache.Cache;
import com.venky.core.math.DoubleUtils;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoder;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.Database;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.PinCode;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Address;
import in.succinct.beckn.Billing;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.BreakUp.BreakUpElement.BreakUpCategory;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.Fulfillment.FulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Images;
import in.succinct.beckn.Item;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Order.NonUniqueItems;
import in.succinct.beckn.Order.Status;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Payment.PaymentType;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.TagGroups;
import in.succinct.beckn.Tags;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class OrderUtil {
    public  OrderUtil(){

    }

    public static void setName(OrderAddress orderAddress, String name) {
        // ./{given_name}/{honorific_prefix}/{first_name}/{middle_name}/{last_name}/{honorific_suffix}'
        /*
        String regex = "^\\./([^/]+)/([^/]*)/([^/]*)/([^/]*)/([^/]*)/([^/]*)$";


        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()){
            throw new RuntimeException("Invalid Name format");
        }
         */
        orderAddress.setLongName(name);

        StringBuilder lastName = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(name," ");
        while (tokenizer.hasMoreTokens()){
            if (ObjectUtil.isVoid(orderAddress.getFirstName())) {
                orderAddress.setFirstName(tokenizer.nextToken());
            }else {
                if (lastName.length() > 0){
                    lastName.append(" ");
                }
                lastName.append(tokenizer.nextElement());
            }
        }

        orderAddress.setLastName(lastName.toString());

    }

    private static String getName(OrderAddress address){
        return address.getLongName();
    }

    public enum OrderFormat {
        selected,
        initialized,
        order,
    }
    public static in.succinct.beckn.Order toBeckn(Order order, OrderFormat format){
        in.succinct.beckn.Order becknOrder = new in.succinct.beckn.Order();
        if (format == OrderFormat.order) {
            becknOrder.setId(BecknUtil.getBecknId(order.getId(), Entity.order));
            becknOrder.setCreatedAt(order.getCreatedAt());
            becknOrder.setUpdatedAt(order.getUpdatedAt());
            becknOrder.set("state",ObjectUtil.equals(order.getFulfillmentStatus(),Order.FULFILLMENT_STATUS_DOWNLOADED)? "INITIATED" :order.getFulfillmentStatus());
        }
        becknOrder.setProvider(new Provider());
        becknOrder.getProvider().setId(BecknUtil.getBecknId(String.valueOf(order.getFacility().getCreatorUserId()), Entity.provider));
        becknOrder.getProvider().setDescriptor(new Descriptor());
        becknOrder.getProvider().getDescriptor().setName(order.getFacility().getName());
        becknOrder.setProviderLocation(new Location());
        becknOrder.getProviderLocation().setId(BecknUtil.getBecknId(String.valueOf(order.getFacilityId()),Entity.provider_location));
        becknOrder.getProviderLocation().setDescriptor(new Descriptor());
        becknOrder.getProviderLocation().getDescriptor().setName(order.getFacility().getName());

        NonUniqueItems items = new NonUniqueItems();
        becknOrder.setItems(items);
        Cache<String, Bucket> buckets = new Cache<String, Bucket>() {
            @Override
            protected Bucket getValue(String s) {
                return new Bucket(0);
            }
        };
        order.getOrderLines().forEach(ol->{
            Item item = new Item();
            item.setId(BecknUtil.getBecknId(String.valueOf(ol.getInventoryId()),Entity.item)); //Change skuId to inventoryId /select jul 7 change
            item.set("tags",getTags(ol.getInventory().getRawRecord().getAsProxy(Inventory.class)));


            Quantity quantity = new Quantity();
            quantity.set("count",(int)ol.getRemainingCancellableQuantity());
            item.set("quantity",quantity);

            item.setDescriptor(new Descriptor());
            item.getDescriptor().setName(ol.getSku().getName());
            Price price = new Price();
            item.setPrice(price);
            price.setValue(ol.getSellingPrice());
            price.setOfferedValue(ol.getSellingPrice());
            price.setListedValue(ol.getMaxRetailPrice());
            price.setCurrency("INR");
            if (!ol.getSku().getAttachments().isEmpty()){
                Images images = new Images();
                images.add(Config.instance().getServerBaseUrl() + ol.getSku().getAttachments().get(0).getAttachmentUrl());
                item.getDescriptor().setImages(images);
            }

            items.add(item);
            buckets.get("MRP").increment(ol.getMaxRetailPrice());
            buckets.get("PRODUCT_SELLING_PRICE").increment(ol.getProductSellingPrice());
            buckets.get("PRODUCT_PRICE").increment(ol.getProductPrice());
            buckets.get("CGST").increment(ol.getCGst());
            buckets.get("SGST").increment(ol.getSGst());
            buckets.get("IGST").increment(ol.getIGst());
        });

        Billing billing = new Billing();
        becknOrder.setBilling(billing);

        Address address = new Address();
        billing.setAddress(address);
        OrderAddress billToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_BILL_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);
        OrderAddress shipToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);

        billing.setName(billToAddress.getFirstName() + " " + StringUtil.valueOf(billToAddress.getLastName()));
        billing.setEmail(billToAddress.getEmail());
        billing.setPhone(billToAddress.getPhoneNumber());

        address.setPinCode(billToAddress.getPinCode().getPinCode());
        address.setDoor(billToAddress.getAddressLine1());
        address.setBuilding(billToAddress.getAddressLine2());
        address.setStreet(billToAddress.getAddressLine3());
        address.setLocality(billToAddress.getAddressLine4());
        address.setCity(billToAddress.getCity().getName());
        address.setState(billToAddress.getState().getName());
        address.setCountry(billToAddress.getCountry().getName());

        Order transport = order.getTransportOrder();
        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setCustomer(new in.succinct.beckn.User());
        fulfillment.getCustomer().setPerson(new Person());
        fulfillment.getCustomer().getPerson().setName(getName(shipToAddress));

        becknOrder.setFulfillment(fulfillment);
        fulfillment.setStart(new FulfillmentStop());
        Location startLocation = new Location();
        fulfillment.getStart().setLocation(startLocation);
        startLocation.setGps(new GeoCoordinate(order.getFacility()));

        fulfillment.setEnd(new FulfillmentStop());
        Location endLocation = new Location();
        fulfillment.setTracking(false);

        fulfillment.getEnd().setLocation(endLocation);
        endLocation.setGps(new GeoCoordinate(shipToAddress));
        if (transport != null){
            fulfillment.setType(FulfillmentType.store_pickup);
            fulfillment.setId(BecknUtil.getBecknId(transport.getId(),Entity.fulfillment));
            fulfillment.setFulfillmentStatus(getFulfillmentStatus(transport));
        }else {
            if (!order.isCustomerPickup()){
                fulfillment.setType(FulfillmentType.home_delivery);
            }else {
                fulfillment.setType(FulfillmentType.store_pickup);
            }
            fulfillment.setId(BecknUtil.getBecknId(order.getId(),Entity.fulfillment));
            if (ObjectUtil.equals(Order.FULFILLMENT_STATUS_SHIPPED,order.getFulfillmentStatus())){
                fulfillment.setFulfillmentStatus(FulfillmentStatus.Out_for_delivery);
            }else if (Arrays.asList(Order.FULFILLMENT_STATUS_DELIVERED,Order.FULFILLMENT_STATUS_RETURNED).contains(order.getFulfillmentStatus())){
                fulfillment.setFulfillmentStatus(FulfillmentStatus.Order_delivered);
            }else {
                fulfillment.setFulfillmentStatus(FulfillmentStatus.Pending);
            }
        }
        if (format.ordinal() >= OrderFormat.initialized.ordinal()){
            Payment payment = new Payment();
            becknOrder.setPayment(payment);
            if (order.getAmountPendingPayment() > 0){
                payment.setStatus(PaymentStatus.NOT_PAID);
                payment.setParams(new Params());
                payment.getParams().setTransactionId("O:"+order.getId());
                payment.getParams().setAmount(order.getAmountPendingPayment());
                payment.getParams().setCurrency("INR");
                User seller = order.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class);
                if (!ObjectUtil.isVoid(seller.getVirtualPaymentAddress())) {
                    StringBuilder url = new StringBuilder();
                    url.append("upi://pay");
                    StringBuilder uriComponent = new StringBuilder();

                    uriComponent.append("?pa=");
                    uriComponent.append(seller.getVirtualPaymentAddress());
                    uriComponent.append("&pn=").append(seller.getNameAsInBankAccount());
                    //uriComponent.append("&tr=O:").append(order.getId());
                    uriComponent.append("&tr=$transaction_id");
                    uriComponent.append("&tn=HumBhiOnline Txn ").append(order.getId());
                    //uriComponent.append("&am=").append(order.getAmountPendingPayment());
                    uriComponent.append("&am=$amount");
                    uriComponent.append("&cu=INR&mode=04&orgId=000000&sign=");
                    url.append(uriComponent);
                    try {
                        payment.setUri(URLEncoder.encode(url.toString(),"UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        payment.setUri(url.toString().replaceAll(" ","%20"));
                    }
                }
            }else {
                payment.setStatus(PaymentStatus.PAID);
            }

            payment.setType(PaymentType.POST_FULFILLMENT);

            buckets.get("SHIPPING_SELLING_PRICE").increment(order.getShippingSellingPrice());
            buckets.get("SHIPPING_PRICE").increment(order.getShippingPrice());
            double shippingTax= order.getShippingSellingPrice() - order.getShippingPrice();
            boolean shippingWithinSameState = ObjectUtil.equals(order.getFacility().getStateId(),shipToAddress.getStateId());
            if (shippingWithinSameState){
                buckets.get("CGST").increment(shippingTax/2.0);
                buckets.get("SGST").increment(shippingTax/2.0);
                buckets.get("IGST").increment(0);
            }else {
                buckets.get("CGST").increment(0);
                buckets.get("SGST").increment(0);
                buckets.get("IGST").increment(shippingTax);
            }

            Quote quote = new Quote();
            becknOrder.setQuote(quote);

            Price price = new Price();
            price.setListedValue(buckets.get("MRP").doubleValue() + buckets.get("SHIPPING_SELLING_PRICE").doubleValue());
            price.setValue(price.getListedValue());
            price.setCurrency("INR");
            quote.setPrice(price);

            Price productPrice = new Price();
            productPrice.setListedValue(buckets.get("MRP").doubleValue());
            productPrice.setValue(price.getListedValue());
            productPrice.setCurrency("INR");

            Price fulfillmentPrice = new Price();
            fulfillmentPrice.setListedValue(order.getShippingSellingPrice());
            fulfillmentPrice.setValue(fulfillmentPrice.getListedValue());
            fulfillmentPrice.setCurrency("INR");

            if (DoubleUtils.compareTo(buckets.get("MRP").doubleValue() ,order.getProductSellingPrice(), 2)>0){
                price.setOfferedValue(order.getProductSellingPrice() + order.getShippingSellingPrice());
                price.setValue(price.getOfferedValue());
                productPrice.setOfferedValue(order.getSellingPrice());
                productPrice.setValue(price.getOfferedValue());
            }


            quote.setTtl(15L*60L); //15 minutes.

            BreakUp breakUp = new BreakUp();
            BreakUpElement element = breakUp.createElement(BreakUpCategory.item,"Total Product", productPrice);
            breakUp.add(element);
            BreakUpElement fulfillmentElement = breakUp.createElement(BreakUpCategory.delivery, "Delivery Charges", fulfillmentPrice);
            breakUp.add(fulfillmentElement);
            quote.setBreakUp(breakUp);

        }
        return becknOrder;
    }


    public static com.venky.swf.plugins.collab.db.model.participants.admin.Address getAddress(in.succinct.beckn.Address address){
        Location location = new Location();
        location.setAddress(address);
        return getAddress(location);
    }
    public static com.venky.swf.plugins.collab.db.model.participants.admin.Address getAddress(final Location location){

        return new com.venky.swf.plugins.collab.db.model.participants.admin.Address() {
            //Location location = stop.getLocation();
            final in.succinct.beckn.Address address = location.getAddress();
            @Override
            public String getAddressLine1() {
                StringBuilder line1 = new StringBuilder();
                if (!ObjectUtil.isVoid(address.getName())){
                    line1.append(address.getName());
                }
                if (!ObjectUtil.isVoid(address.getDoor())){
                    if (line1.length() > 0){
                        line1.append(" ");
                    }
                    line1.append(address.getDoor());
                }
                if (!ObjectUtil.isVoid(address.getBuilding())){
                    if (line1.length() > 0){
                        line1.append(" ");
                    }
                    line1.append(address.getBuilding());
                }
                return line1.toString();
            }

            @Override
            public void setAddressLine1(String line1) {

            }

            @Override
            public String getAddressLine2() {
                StringBuilder line = new StringBuilder();
                if (!ObjectUtil.isVoid(address.getStreet())){
                    line.append(address.getStreet());
                }
                if (!ObjectUtil.isVoid(address.getLocality())){
                    if (line.length() > 0){
                        line.append(" ");
                    }
                    line.append(address.getLocality());
                }
                return line.toString();
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
                    city = City.findByCode(address.getCity());
                    if (city == null){
                        city = Database.getTable(City.class).newRecord();
                        city.setCode(address.getCity());
                        city.setStateId(getStateId());
                        city.save();
                    }
                }
                return city;
            }

            State state = null;
            @Override
            public Long getStateId() {
                return getState().getId();
            }

            @Override
            public void setStateId(Long stateId) {

            }

            @Override
            public Long getCountryId() {
                return getCountry().getId();
            }

            @Override
            public State getState() {
                if (state == null){
                    state = State.findByCountryAndName(getCountryId(),address.getState());
                }
                return state;
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
            public void setPinCodeId(Long pinCodeId) {

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
            BigDecimal lat = null;
            BigDecimal lng = null ;
            public void loadGps(){
                if (lat != null && lng != null){
                    return;
                }
                GeoCoordinate gps = location.getGps();
                if (gps != null) {
                    lat = gps.getLat();
                    lng = gps.getLng();
                }else {
                    Map<String,String> params = new HashMap<>();
                    params.put("here.app_id", Config.instance().getProperty("geocoder.here.app_id"));
                    params.put("here.app_code", Config.instance().getProperty("geocoder.here.app_code"));
                    params.put("here.app_key", Config.instance().getProperty("geocoder.here.app_key"));
                    params.put("google.api_key", Config.instance().getProperty("geocoder.google.api_key"));
                    StringBuilder addressString = new StringBuilder();
                    addressString.append(getAddressLine1()).append(" ").append(getAddressLine2()).append( " ").append( getCity().getName());
                    for (GeoCoder coder : new GeoCoder[] { new GeoCoder("google") , new GeoCoder("here") }){
                        if (coder.isEnabled(params)){
                            GeoLocation revEncodedLocation = coder.getLocation(addressString.toString(),params);
                            if (revEncodedLocation != null){
                                setLat(revEncodedLocation.getLat());
                                setLng(revEncodedLocation.getLng());
                                location.setGps(new GeoCoordinate(revEncodedLocation));
                            }
                        }
                    }

                }
            }
            @Override
            public BigDecimal getLat() {
                loadGps();
                return lat;
            }

            @Override
            public void setLat(BigDecimal latitude) {
                this.lat = latitude;
            }

            @Override
            public BigDecimal getLng() {
                loadGps();
                return lng;
            }

            @Override
            public void setLng(BigDecimal longitude) {
                this.lng = longitude;
            }
        };
    }

    public static double getDeliveryCharges(com.venky.swf.plugins.collab.db.model.participants.admin.Address deliveryLocation, Facility facility) {
        if (!facility.isDeliveryProvided()){
            throw new RuntimeException("Seller does not provide home delivery.");
        }

        if (deliveryLocation == null){
            throw new RuntimeException("Could not locate delivery address");
        }else {
            double distance = new GeoCoordinate(deliveryLocation).distanceTo(new GeoCoordinate(facility));
            if (facility.getDeliveryRadius() < distance){
                throw new RuntimeException("Seller does not provide home delivery beyond " + facility.getDeliveryRadius());
            }else {
                return facility.getDeliveryCharges(distance,false);
            }
        }

    }
    public static Facility getOrderFacility(User seller, Locations locations) {
        Location providerLocation = locations.size() > 0 ? locations.get(0) : null;

        Long facilityId = providerLocation != null ? Long.valueOf(BecknUtil.getLocalUniqueId(providerLocation.getId(), Entity.provider_location)) : null;
        if (facilityId == null){
            List<Long> operatingFacilityIds =  seller.getOperatingFacilityIds();
            if (!operatingFacilityIds.isEmpty()){
                facilityId = operatingFacilityIds.get(0);
            }else {
                throw new RuntimeException("Don't know what facility the order was made into!");
            }
        }
        return Database.getTable(Facility.class).get(facilityId);

    }

    public static Status getBecknStatus (Order order){
        Status status = orderStatusMap.get(order.getFulfillmentStatus());
        if (status == null){
            status = Status.Accepted;
        }
        return status;
    }
    private static FulfillmentStatus getFulfillmentStatus(Order transport) {
        FulfillmentStatus fulfillmentStatus = fulfillmentStatusMap.get(transport.getFulfillmentStatus());
        if (fulfillmentStatus == null){
            fulfillmentStatus = FulfillmentStatus.Pending;
        }
        return fulfillmentStatus;
    }
    private static final Map<String, Fulfillment.FulfillmentStatus> fulfillmentStatusMap = new HashMap<>(){{
        put(Order.FULFILLMENT_STATUS_SHIPPED, FulfillmentStatus.Out_for_delivery);
        put(Order.FULFILLMENT_STATUS_DELIVERED, FulfillmentStatus.Order_delivered);
    }};

    private static final Map<String, Status> orderStatusMap = new HashMap<>(){{
        put(Order.FULFILLMENT_STATUS_DOWNLOADED, Status.Created);
        put(Order.FULFILLMENT_STATUS_ACKNOWLEDGED, Status.Accepted);
        put(Order.FULFILLMENT_STATUS_SHIPPED,Status.Out_for_delivery);
        put(Order.FULFILLMENT_STATUS_DELIVERED, Status.Completed);
        put(Order.FULFILLMENT_STATUS_CANCELLED, Status.Cancelled);
    }};

    public static Tags getTags(Inventory inventory){
        Tags tags = new Tags();
        String seoTags = inventory.getTags();
        if (seoTags != null) {
            StringTokenizer tokenizer = new StringTokenizer(seoTags, ", ");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                String[] parts = token.split("=");
                String key;
                String value;
                if (parts.length == 2) {
                    key = parts[0];
                    value = parts[1];
                } else {
                    key = token;
                    value = "Y";
                }
                tags.set(key, value);
            }
        }
        return tags;
    }
}
