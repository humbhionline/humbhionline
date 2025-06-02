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
import in.succinct.beckn.BreakUp.BreakUpElement.BreakUpCategory;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Document;
import in.succinct.beckn.Documents;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentStatus;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Images;
import in.succinct.beckn.Item;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Order.NonUniqueItems;
import in.succinct.beckn.Order.Status;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payments;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.TagGroups;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            switch (order.getFulfillmentStatus()){
                case Order.FULFILLMENT_STATUS_DOWNLOADED:
                    becknOrder.setStatus(Status.Created);
                    break;
                case Order.FULFILLMENT_STATUS_ACKNOWLEDGED:
                    becknOrder.setStatus(Status.Accepted);
                    break;
                case Order.FULFILLMENT_STATUS_PACKED:
                case Order.FULFILLMENT_STATUS_MANIFESTED:
                    becknOrder.setStatus(Status.Prepared);
                    break;
                case Order.FULFILLMENT_STATUS_SHIPPED:
                    becknOrder.setStatus(Status.In_Transit);
                    break;
                case Order.FULFILLMENT_STATUS_DELIVERED:
                    becknOrder.setStatus(Status.Completed);
                    break;
                case Order.FULFILLMENT_STATUS_CANCELLED:
                case Order.FULFILLMENT_STATUS_RETURNED:
                    becknOrder.setStatus(Status.Cancelled);
                    break;
            }
        }
        becknOrder.setProvider(new Provider());
        becknOrder.getProvider().setId(BecknUtil.getBecknId(String.valueOf(order.getFacility().getCreatorUserId()), Entity.provider));
        becknOrder.getProvider().setDescriptor(new Descriptor());
        becknOrder.getProvider().getDescriptor().setName(order.getFacility().getName());
        becknOrder.setProviderLocation(new Location());
        becknOrder.getProviderLocation().setId(BecknUtil.getBecknId(String.valueOf(order.getFacilityId()),Entity.provider_location));
        becknOrder.getProviderLocation().setDescriptor(new Descriptor());
        becknOrder.getProviderLocation().getDescriptor().setName(order.getFacility().getName());
        becknOrder.getProvider().setLocations(new Locations());
        becknOrder.getProvider().getLocations().add(new Location(){{
            setId(BecknUtil.getBecknId(String.valueOf(order.getFacilityId()),Entity.provider_location));
        }});
        becknOrder.setDocuments(new Documents());
        order.getOrderPrints().forEach(op-> {
                Document document = new Document();
                document.setLabel(op.getImageContentName());
                document.setUrl(Config.instance().getServerBaseUrl()+"/order_prints/view/"+op.getId());
                becknOrder.getDocuments().add(document);
        });
        NonUniqueItems items = new NonUniqueItems();
        becknOrder.setItems(items);
        Cache<String, Bucket> buckets = new Cache<>() {
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
            quantity.setCount((int)ol.getRemainingCancellableQuantity());
            item.setQuantity(quantity);

            item.setDescriptor(new Descriptor());
            item.getDescriptor().setName(ol.getSku().getName());
            Price price = new Price();
            item.setPrice(price);
            price.setValue(ol.getRemainingCancellableQuantity() * ol.getSellingPrice()/ol.getOrderedQuantity());
            price.setOfferedValue(price.getValue());
            price.setListedValue(ol.getRemainingCancellableQuantity() * ol.getMaxRetailPrice()/ol.getOrderedQuantity());

            price.setCurrency("INR");
            if (!ol.getSku().getAttachments().isEmpty()){
                Images images = new Images();
                images.add(Config.instance().getServerBaseUrl() + ol.getSku().getAttachments().get(0).getAttachmentUrl());
                item.getDescriptor().setImages(images);
            }

            items.add(item);
            buckets.get("MRP").increment(ol.getRemainingCancellableQuantity() * ol.getMaxRetailPrice()/ol.getOrderedQuantity());
            buckets.get("PRODUCT_SELLING_PRICE").increment(ol.getRemainingCancellableQuantity() * ol.getProductSellingPrice()/ol.getOrderedQuantity());
            buckets.get("PRODUCT_PRICE").increment(ol.getRemainingCancellableQuantity() * ol.getProductPrice()/ol.getOrderedQuantity());
            buckets.get("CGST").increment(ol.getRemainingCancellableQuantity() * ol.getCGst()/ol.getOrderedQuantity());
            buckets.get("SGST").increment(ol.getRemainingCancellableQuantity() * ol.getSGst()/ol.getOrderedQuantity());
            buckets.get("IGST").increment(ol.getRemainingCancellableQuantity() * ol.getIGst()/ol.getOrderedQuantity());
        });



        OrderAddress billToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_BILL_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);
        becknOrder.setBilling(new Billing(){{
            setAddress(OrderUtil.getAddress(billToAddress).flatten());
            setName(billToAddress.getFirstName() + " " + StringUtil.valueOf(billToAddress.getLastName()));
            setEmail(billToAddress.getEmail());
            setPhone(billToAddress.getPhoneNumber());
        }});


        OrderAddress shipToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);

        Order transport = order.getTransportOrder();
        becknOrder.setFulfillment(new Fulfillment(){{
            setCustomer(new in.succinct.beckn.User(){{
                setPerson(new Person(){{
                    setName(OrderUtil.getName(shipToAddress));
                }});
            }});
            _setStart(new FulfillmentStop(){{
                setLocation(new Location(){{
                    setGps(new GeoCoordinate(order.getFacility()));
                    setId(becknOrder.getProviderLocation().getId());
                }});
                setContact(new Contact(){{
                    setPhone(order.getFacility().getPhoneNumber());
                    setEmail(order.getFacility().getEmail());
                }});
            }});
            _setEnd(new FulfillmentStop(){{
                Location endLocation = new Location();
                endLocation.setGps(new GeoCoordinate(shipToAddress));
                endLocation.setCountry(new in.succinct.beckn.Country(){{
                    setCode(shipToAddress.getCountry().getCode());
                    setName(shipToAddress.getCountry().getName());
                }});
                endLocation.setState(new in.succinct.beckn.State(){{
                    setCode(shipToAddress.getState().getCode());
                    setName(shipToAddress.getState().getName());
                }});
                endLocation.setPinCode(shipToAddress.getPinCode().getPinCode());
                endLocation.setCity(new in.succinct.beckn.City(){{
                    setCode(shipToAddress.getCity().getCode());
                    setName(shipToAddress.getCity().getName());
                }});
                endLocation.setAddress(getAddress(shipToAddress));
                setLocation(endLocation);
                setContact(new Contact(){{
                    setPhone(shipToAddress.getPhoneNumber());
                    setEmail(shipToAddress.getEmail());
                }});
                setPerson(new Person(){{
                    setName(OrderUtil.getName(shipToAddress));
                }});
            }});
            setTracking(false);
            if (transport != null){
                setType(RetailFulfillmentType.store_pickup.toString());
                setId(BecknUtil.getBecknId(transport.getId(),Entity.fulfillment));
                setFulfillmentStatus(OrderUtil.getFulfillmentStatus(transport));
            }else {
                if (!order.isCustomerPickup()){
                    setType(RetailFulfillmentType.home_delivery.toString());
                }else {
                    setType(RetailFulfillmentType.store_pickup.toString());
                }
                setId(BecknUtil.getBecknId(order.getId(),Entity.fulfillment));
                if (ObjectUtil.equals(Order.FULFILLMENT_STATUS_SHIPPED,order.getFulfillmentStatus())){
                    setFulfillmentStatus(FulfillmentStatus.In_Transit);
                }else if (Arrays.asList(Order.FULFILLMENT_STATUS_DELIVERED,Order.FULFILLMENT_STATUS_RETURNED).contains(order.getFulfillmentStatus())){
                    setFulfillmentStatus(FulfillmentStatus.Completed);
                }else {
                    setFulfillmentStatus(FulfillmentStatus.Preparing);
                }
            }
        }});



        if (format.ordinal() >= OrderFormat.initialized.ordinal()){
            becknOrder.setPayments(new Payments(){{
                add(new Payment(){{
                    setParams(new Params(){{
                        set("transaction_id",order.getExternalTransactionReference());
                        setAmount(order.getAmountPendingPayment());
                        setCurrency("INR");
                    }});
                    if (order.getAmountPendingPayment() > 0){
                        setStatus(PaymentStatus.NOT_PAID);
                        User seller = order.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class);
                        if (!ObjectUtil.isVoid(seller.getVirtualPaymentAddress())) {
                            StringBuilder url = new StringBuilder();
                            url.append("upi://pay").append("?pa=").append(seller.getVirtualPaymentAddress()).append("&pn=").append(seller.getNameAsInBankAccount()).append(
                                    "&tr=$transaction_id").append("&tn=HumBhiOnline Txn ").append(order.getId()).append(
                                    "&am=$amount").append("&cu=INR&mode=04&orgId=000000&sign=");
                            
                            setUri(URLEncoder.encode(url.toString(), StandardCharsets.UTF_8));
                        }
                    }else if (order.getAmountToRefund() > 0){
                        setStatus(PaymentStatus.NOT_PAID);
                    }else {
                        setStatus(PaymentStatus.PAID);
                    }
                    
                    setPaymentType(Payment.POST_FULFILLMENT);
                }});
            }});

            if (order.getAmountPaid() + order.getAmountPendingPayment() - order.getAmountRefunded() > 0) {
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
            }

            becknOrder.setQuote(new Quote(){{
                setPrice(new Price(){{
                    setListedValue(buckets.get("MRP").doubleValue() + buckets.get("SHIPPING_SELLING_PRICE").doubleValue());
                    setOfferedValue(buckets.get("PRODUCT_SELLING_PRICE").doubleValue()+ buckets.get("SHIPPING_SELLING_PRICE").doubleValue());
                    setValue(getOfferedValue());
                    setCurrency("INR");

                    if (DoubleUtils.compareTo(getListedValue() , getOfferedValue())>0) {
                        setOfferedValue(null);
                    }
                }});

                setTtl(15L*60L); //15 minutes.
                setBreakUp(new BreakUp(){{
                    add(createElement(BreakUpCategory.item,"Total Product", new Price() {{
                        setListedValue(buckets.get("MRP").doubleValue());
                        setOfferedValue(buckets.get("PRODUCT_SELLING_PRICE").doubleValue());
                        setValue(getOfferedValue());
                        setCurrency("INR");
                        if (DoubleUtils.compareTo(getListedValue(),getOfferedValue()) > 0){
                            setOfferedValue(null);
                        }
                    }}));
                    add(createElement(BreakUpCategory.delivery, "Delivery Charges", new Price(){{
                        setListedValue(buckets.get("SHIPPING_SELLING_PRICE").doubleValue());
                        setValue(getListedValue());
                        setCurrency("INR");
                    }}));
                }});
            }});
        }
        return becknOrder;
    }



    public static Address getAddress(com.venky.swf.plugins.collab.db.model.participants.admin.Address localAddress){
        Address address = new Address();
        address.setState(localAddress.getState().getName());
        if (localAddress instanceof OrderAddress) {
            address.setName(((OrderAddress)localAddress).getLongName());
        }else if (localAddress instanceof Facility){
            address.setName(((Facility)localAddress).getName());
        }
        address.setPinCode(localAddress.getPinCode().getPinCode());
        address.setCity(localAddress.getCity().getCode());
        address.setCountry(localAddress.getCountry().getIsoCode());
        address.setDoor(localAddress.getAddressLine1());
        address.setBuilding(localAddress.getAddressLine2());
        address.setStreet(localAddress.getAddressLine3());
        address.setLocality(localAddress.getAddressLine4());
        return address;
    }
    public static com.venky.swf.plugins.collab.db.model.participants.admin.Address getAddress(final Location location){

        com.venky.swf.plugins.collab.db.model.participants.admin.Address address =  getAddress(location.getAddress(),location);

        return address;
    }
    public static com.venky.swf.plugins.collab.db.model.participants.admin.Address getAddress(final Address address , Location location){

        return new com.venky.swf.plugins.collab.db.model.participants.admin.Address() {

            //Location location = stop.getLocation();
            @Override
            public String getAddressLine1() {
                StringBuilder line = new StringBuilder();
                if (!ObjectUtil.isVoid(address.getDoor())){
                    line.append(address.getDoor());
                }
                if (!ObjectUtil.isVoid(address.getBuilding())){
                    if (line.length() > 0){
                        line.append(" ");
                    }
                    line.append(address.getBuilding());
                }
                return line.toString();
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
                if (!ObjectUtil.isVoid(address.getWard())){
                    if (line.length() > 0){
                        line.append(" ");
                    }
                    line.append(address.getWard());
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
            public String getLandmark() {
                return "";
            }
            
            @Override
            public void setLandmark(String landmark) {
            
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
                    city = City.findByStateAndName(getStateId(),address.getCity(),true);
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
                    country = Country.findByName(address.getCountry());
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
                if (location == null){
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
                    GeoCoder coder = GeoCoder.getInstance();
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
            fulfillmentStatus = FulfillmentStatus.Preparing;
        }
        return fulfillmentStatus;
    }
    private static final Map<String, Fulfillment.FulfillmentStatus> fulfillmentStatusMap = new HashMap<>(){{
        put(Order.FULFILLMENT_STATUS_SHIPPED, FulfillmentStatus.In_Transit);
        put(Order.FULFILLMENT_STATUS_DELIVERED, FulfillmentStatus.Completed);
    }};

    private static final Map<String, Status> orderStatusMap = new HashMap<>(){{
        put(Order.FULFILLMENT_STATUS_DOWNLOADED, Status.Created);
        put(Order.FULFILLMENT_STATUS_ACKNOWLEDGED, Status.Accepted);
        put(Order.FULFILLMENT_STATUS_SHIPPED,Status.In_Transit);
        put(Order.FULFILLMENT_STATUS_DELIVERED, Status.Completed);
        put(Order.FULFILLMENT_STATUS_CANCELLED, Status.Cancelled);
    }};

    public static TagGroups getTags(Inventory inventory){
        TagGroups tags = new TagGroups();
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
                tags.setTag("general_attributes",key, value);
            }
        }
        return tags;
    }
}
