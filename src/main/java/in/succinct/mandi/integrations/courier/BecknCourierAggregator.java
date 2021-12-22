package in.succinct.mandi.integrations.courier;

import com.venky.core.math.DoubleUtils;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Context;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Images;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.integrations.beckn.MessageCallbackUtil;
import in.succinct.mandi.integrations.courier.Courier.CourierDescriptor;
import in.succinct.mandi.integrations.courier.Courier.CourierOrder;
import in.succinct.mandi.integrations.courier.Courier.Quote;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

class BecknCourierAggregator implements CourierAggregator {
    private final BecknNetwork network ;
    public BecknCourierAggregator(BecknNetwork network){
        this.network = network;
    }

    private Context makeContext(){
        Context context = new Context();
        context.setDomain("nic2004:55204");
        context.setCity("std:080");
        context.setCountry("IND");

        context.setBapId(network.getDeliveryBapSubscriberId());
        context.setBapUri(network.getDeliveryBapUrl());
        context.setTimestamp(new Timestamp(System.currentTimeMillis()));
        context.setMessageId(UUID.randomUUID().toString());
        context.setTransactionId(context.getMessageId());
        context.setCoreVersion("0.9.1");
        context.setTtl(30);
        context.setKey(BecknUtil.getSelfEncryptionKey().getPublicKey());
        return context;
    }
    private FulfillmentStop makeFulfillmentStop(Address address){
        FulfillmentStop stop = new FulfillmentStop();
        Location location = new Location();

        stop.setLocation(location);

        in.succinct.beckn.Address address1 = new in.succinct.beckn.Address();
        location.setAddress(address1);

        address1.setPinCode(address.getPinCode().getPinCode());
        address1.setDoor(address.getAddressLine1());
        address1.setBuilding(address.getAddressLine2());
        address1.setStreet(address.getAddressLine3());
        address1.setLocality(address.getAddressLine4());
        address1.setCity(address.getCity().getCode());
        address1.setState(address.getState().getCode());
        address1.setCountry(address.getCountry().getIsoCode());
        location.setGps(new GeoCoordinate(address.getLat(),address.getLng()));

        return stop;
    }
    private Message makeSearchMessage(Order retailOrder){
        return makeSearchMessage(retailOrder.getFacility(),retailOrder.getShipToAddress(),retailOrder.getOrderLines().get(0).getInventory().getRawRecord().getAsProxy(Inventory.class));
    }
    private Message makeSearchMessage(Address from, Address to, Inventory inventory){
        Message message = new Message();

        Intent intent = new Intent();
        message.setIntent(intent);

        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setStart(makeFulfillmentStop(from));
        fulfillment.setEnd(makeFulfillmentStop(to));
        intent.setFulfillment(fulfillment);
        return message;
    }
    private Request makeSearchJson(Address from, Address to, Inventory inventory){
        Request request = new Request();
        Context context = makeContext();
        context.setAction("search");
        request.setContext(context);
        Message message = makeSearchMessage(from,to, inventory);
        request.setMessage(message);
        return request;
    }
    private Request makeSearchJson(Order retailOrder){
        Request request = new Request();
        Context context = makeContext();
        context.setAction("search");
        request.setContext(context);
        Message message = makeSearchMessage(retailOrder);
        request.setMessage(message);
        return request;
    }
    private Request makeConfirmJson(Order transportOrder, Order parentOrder){
        Request request = new Request();
        Context context = makeContext();
        context.setAction("confirm");
        request.setContext(context);
        Message message = makeConfirmMessage(transportOrder,parentOrder);
        request.setMessage(message);
        return request;
    }
    private Message makeConfirmMessage(Order transportOrder,Order parentOrder){
        Message message = new Message();

        Address from = parentOrder.getFacility();
        Address to = parentOrder.getShipToAddress();

        in.succinct.beckn.Order order = new in.succinct.beckn.Order();
        message.setOrder(order);

        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setStart(makeFulfillmentStop(from));
        fulfillment.setEnd(makeFulfillmentStop(to));
        order.setFulfillment(fulfillment);
        Item item = new Item();
        Items items = new Items();
        order.setItems(items);
        items.add(item);
        item.setId("0");
        return message;
    }
    JSONObject gw = null;
    @SuppressWarnings("unchecked")
    public JSONObject getGateway(){
        if (gw == null){
            JSONObject input = new JSONObject();
            input.put("type","BG");
            input.put("domain","nic2004:55204");input.put("city","std:080");input.put("country","IND");
            JSONArray out = BecknPublicKeyFinder.lookup(network,input);
            if (out != null && out.size() > 0){
                gw = (JSONObject)out.get(0);
            }
        }

        return gw;
    }

    private List<OnSearch> search(Request request) {
        JSONObject gw = getGateway();
        String authHeader = request.generateAuthorizationHeader(request.getContext().getBapId(),BecknUtil.getCryptoKeyId());

        InputStream responseStream = new Call<>().url((String)gw.get("subscriber_url")).header("content-type","application/json").header("accept","application/json").
                header("Authorization", authHeader).inputFormat(InputFormat.JSON).input(request.toString()).getResponseStream();

        Response response = new Response(StringUtil.read(responseStream));
        List<OnSearch> onSearches = new ArrayList<>();
        if (response.getAcknowledgement().getStatus() == Status.ACK) {
            JSONObject aResponse;
            while ((aResponse = MessageCallbackUtil.getInstance().getNextResponse(request.getContext().getMessageId(),3000L)) != null){
                onSearches.add(new OnSearch(aResponse));
            }
        }
        return onSearches;
    }


    @Override
    public List<Quote> getQuotes(Order retailOrder) {
        Request searchRequest = makeSearchJson(retailOrder);
        return getQuotes(searchRequest);
    }

    @Override
    public Order getOrder(JSONObject statusJson) {
        OnStatus onStatus = new OnStatus(statusJson);
        return Order.find(onStatus.getContext().getTransactionId());
    }

    @Override
    public CourierOrder getCourierOrder(JSONObject statusJson) {
        OnStatus onStatus = new OnStatus(statusJson);
        in.succinct.beckn.Order becknOrder =  onStatus.getMessage().getOrder();

        return new CourierOrder() {
            @Override
            public String getTrackingUrl() {
                return "";
            }

            @Override
            public String getOrderNumber() {
                return onStatus.getContext().getTransactionId();
            }

            @Override
            public boolean isCompleted() {
                return (
                        becknOrder.getState().toLowerCase(Locale.ROOT).startsWith("complete") ||
                                becknOrder.getState().toLowerCase(Locale.ROOT).startsWith("delivered")
                );
            }

            @Override
            public CourierDescriptor getCourierDescriptor() {
                Provider provider = becknOrder.getProvider();
                return new CourierDescriptor() {
                    @Override
                    public String getId() {
                        return provider.getId();
                    }

                    @Override
                    public String getName() {
                        return provider.getDescriptor().getName();
                    }

                    @Override
                    public String getLogoUrl() {
                        Images images = provider.getDescriptor().getImages();
                        if (images.size() > 0){
                            return images.get(0);
                        }
                        return null;
                    }
                };
            }

            @Override
            public double getSellingPrice() {
                return becknOrder.getPayment().getDouble("amount");
            }

        };
    }

    @Override
    public CourierOrder book(Order transportOrder, Order parentOrder) {
        Request confirmRequest = makeConfirmJson(transportOrder,parentOrder);
        if (ObjectUtil.isVoid(transportOrder.getExternalTransactionReference())){
            throw new RuntimeException("Cannot book with out searching first.");
        }

        confirmRequest.getContext().setTransactionId(transportOrder.getExternalTransactionReference());

        String authHeader = confirmRequest.generateAuthorizationHeader(confirmRequest.getContext().getBapId(),BecknUtil.getCryptoKeyId());


        InputStream responseStream = new Call<>().url(confirmRequest.getContext().getBppUri()).header("content-type","application/json").header("accept","application/json").
                header("Authorization", authHeader).inputFormat(InputFormat.JSON).input(confirmRequest.toString()).getResponseStream();

        Response response = new Response(StringUtil.read(responseStream));
        if (response.getAcknowledgement().getStatus() == Status.ACK) {
            JSONObject aResponse = MessageCallbackUtil.getInstance().getNextResponse(confirmRequest.getContext().getMessageId(),3000L);
            OnConfirm onConfirm = new OnConfirm(aResponse);
        }

        return null;
    }

    private List<Quote> getQuotes(Request searchRequest){
        List<OnSearch> onSearches = search(searchRequest);

        List<Quote> quotes = new ArrayList<>();
        onSearches.forEach(onSearch-> {
            Providers providers = onSearch.getMessage().getCatalog().getProviders();
            for (Provider provider : providers){
                for (Item item : provider.getItems()){
                    quotes.add(new Quote() {
                        @Override
                        public CourierDescriptor getCourierDescriptor() {
                            return new CourierDescriptor() {
                                @Override
                                public String getId() {
                                    return provider.getId();
                                }

                                @Override
                                public String getName() {
                                    return provider.getDescriptor().getName();
                                }

                                @Override
                                public String getLogoUrl() {
                                    Images images = provider.getDescriptor().getImages();
                                    if (images.size() > 0){
                                        return images.get(0);
                                    }
                                    return null;
                                }

                            };
                        }
                        public String getQuoteRef(){
                            return onSearch.getContext().getTransactionId();
                        }

                        @Override
                        public double getSellingPrice() {
                            return item.getPrice().getEstimatedValue();
                        }

                    });
                }
            }



        });
        return quotes;
    }

    @Override
    public List<Quote> getQuotes(Address from, Address to, Inventory inventory) {
        Request searchRequest = makeSearchJson(from,to,inventory);
        return getQuotes(searchRequest);
    }
}
