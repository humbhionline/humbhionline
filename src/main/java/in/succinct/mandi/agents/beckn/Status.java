package in.succinct.mandi.agents.beckn;


import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.Database;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.beckn.Address;
import in.succinct.beckn.Billing;
import in.succinct.beckn.Billing.BillToLocation;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Person;
import in.succinct.beckn.Person.Name;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONObject;
import org.owasp.encoder.Encode;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Status extends BecknAsyncTask {

    public Status(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        String orderId = request.getMessage().get("order_id");
        Long lOrderId = Long.valueOf(BecknUtil.getLocalUniqueId(orderId, Entity.order));
        Order order = Database.getTable(Order.class).get(lOrderId);

        in.succinct.beckn.Order becknOrder = new in.succinct.beckn.Order();
        becknOrder.setId(orderId);
        becknOrder.setCreatedAt(order.getCreatedAt());
        becknOrder.setUpdatedAt(order.getUpdatedAt());
        becknOrder.set("state",order.getFulfillmentStatus());
        becknOrder.setProvider(new Provider());
        becknOrder.getProvider().setId(BecknUtil.getBecknId(String.valueOf(order.getFacility().getCreatorUserId()),Entity.provider));
        becknOrder.setProviderLocation(new Location());
        becknOrder.getProviderLocation().setId(BecknUtil.getBecknId(String.valueOf(order.getFacilityId()),Entity.provider_location));
        Items items = new Items();
        becknOrder.setItems(items);
        order.getOrderLines().forEach(ol->{
            Item item = new Item();
            item.setId(BecknUtil.getBecknId(String.valueOf(ol.getSkuId()),Entity.item));
            item.set("quantity",ol.getRemainingCancellableQuantity());
        });
        Billing billing = new Billing();
        becknOrder.setBilling(billing);

        Person customer = new Person();
        billing.setCustomer(customer);

        Name customerName = new Name();
        customer.setName(customerName);
        customerName.setGivenName(order.getCreatorUser().getLongName());


        billing.setLocation(new BillToLocation());
        Address address = new Address();
        billing.getLocation().setAddress(address);
        OrderAddress billToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_BILL_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);
        OrderAddress shipToAddress = order.getAddresses().stream().filter(a-> ObjectUtil.equals(a.getAddressType(), OrderAddress.ADDRESS_TYPE_SHIP_TO)).findFirst().get().getRawRecord().getAsProxy(OrderAddress.class);

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
        becknOrder.setFulfillment(fulfillment);
        fulfillment.setStart(new FulfillmentStop());
        Location startLocation = new Location();
        fulfillment.getStart().setLocation(startLocation);
        startLocation.setGps(new GeoCoordinate(order.getFacility()));

        fulfillment.setEnd(new FulfillmentStop());
        Location endLocation = new Location();

        fulfillment.getEnd().setLocation(endLocation);
        endLocation.setGps(new GeoCoordinate(shipToAddress));
        if (transport != null){
            fulfillment.setType(FulfillmentType.home_delivery);
            fulfillment.setId(BecknUtil.getBecknId(transport.getId(),Entity.fulfillment));
            fulfillment.setState(transport.getFulfillmentStatus());
        }else {
            if (!order.isCustomerPickup()){
                fulfillment.setType(FulfillmentType.home_delivery);
            }else {
                fulfillment.setType(FulfillmentType.store_pickup);
            }
            fulfillment.setId(BecknUtil.getBecknId(order.getId(),Entity.fulfillment));
            if (ObjectUtil.equals(Order.FULFILLMENT_STATUS_SHIPPED,order.getFulfillmentStatus())){
                fulfillment.setState(order.getFulfillmentStatus());
            }else if (Arrays.asList(Order.FULFILLMENT_STATUS_DELIVERED,Order.FULFILLMENT_STATUS_RETURNED).contains(order.getFulfillmentStatus())){
                fulfillment.setState(Order.FULFILLMENT_STATUS_DELIVERED);
            }else {
                fulfillment.setState(Order.FULFILLMENT_STATUS_DOWNLOADED);
            }
        }
        Payment payment = new Payment();
        becknOrder.setPayment(payment);
        if (order.getAmountPendingPayment() > 0){
            payment.setStatus("NOT-PAID");
            payment.setParams(new Params());
            payment.getParams().setTransactionId("O:"+order.getId());
            payment.getParams().setAmount(order.getAmountPendingPayment());
            User seller = order.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class);
            if (!ObjectUtil.isVoid(seller.getVirtualPaymentAddress())) {
                StringBuilder url = new StringBuilder();
                url.append("upi://pay");
                StringBuilder uriComponent = new StringBuilder();

                uriComponent.append("?pa=");
                uriComponent.append(seller.getVirtualPaymentAddress());
                uriComponent.append("&pn=").append(seller.getNameAsInBankAccount());
                uriComponent.append("&tr=O:").append(order.getId());
                uriComponent.append("&tn=HumBhiOnline Txn ").append(order.getId());
                uriComponent.append("&am=").append(order.getAmountPendingPayment());
                uriComponent.append("&cu=INR&mode=04&orgId=000000&sign=");
                url.append(uriComponent);
                try {
                    payment.setUri(URLEncoder.encode(url.toString(),"UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    payment.setUri(url.toString().replaceAll(" ","%20"));
                }
            }
        }else {
            payment.setStatus("PAID");
        }

        payment.setType("POST-FULFILLMENT");

        OnStatus onStatus = new OnStatus();
        onStatus.setContext(request.getContext());
        onStatus.setMessage(new Message());
        onStatus.getMessage().setOrder(becknOrder);

        new Call<JSONObject>().url(getRequest().getContext().getBapUri() + "/on_status").
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(becknOrder.getInner()).headers(getHeaders(onStatus)).getResponseAsJson();

    }
    private Map<String, String> getHeaders(OnStatus onStatus) {
        Map<String,String> headers  = new HashMap<>();
        headers.put("Authorization",onStatus.generateAuthorizationHeader(onStatus.getContext().getBppId(),onStatus.getContext().getBppId() + ".k1"));
        return headers;
    }

}
