package in.succinct.mandi.integrations.courier;

import com.venky.core.string.StringUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.AddOns;
import in.succinct.beckn.Billing;
import in.succinct.beckn.Category;
import in.succinct.beckn.Contact;
import in.succinct.beckn.Context;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Message;
import in.succinct.beckn.Offers;
import in.succinct.beckn.OnConfirm;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.OnStatus;
import in.succinct.beckn.Payment;
import in.succinct.beckn.Payment.Params;
import in.succinct.beckn.Person;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.db.model.BillToAddress;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.extensions.BecknPublicKeyFinder;
import in.succinct.mandi.integrations.beckn.MessageCallbackUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.order.PersonAddress;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        context.setBapUri(Config.instance().getServerBaseUrl() + "/" + network.getDeliveryBapUrl());
        context.setTimestamp(new Timestamp(System.currentTimeMillis()));
        context.setMessageId(UUID.randomUUID().toString());
        context.setTransactionId(context.getMessageId());
        context.setCoreVersion("0.9.1");
        context.setTtl(6);
        //context.setKey(BecknUtil.getSelfEncryptionKey(network).getPublicKey());
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
        stop.setContact(new Contact());
        stop.setPerson(new Person());
        if (address instanceof PersonAddress) {
            stop.getPerson().setName(((PersonAddress)address).getLongName());
        }else if (address.getClass().getSimpleName().equals(Facility.class.getSimpleName())){
            stop.getPerson().setName(((Facility)address).getCreatorUser().getLongName());
        }
        stop.getContact().setEmail(address.getEmail());
        stop.getContact().setPhone(address.getPhoneNumber());

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
    private Request makeConfirmJson(Inventory inventory, Order courierOrder, Order parentOrder){
        Request request = new Request();
        Context context = makeContext();
        context.setAction("confirm");
        request.setContext(context);


        Message message = makeConfirmMessage(context,inventory,courierOrder,parentOrder);
        request.setMessage(message);

        return request;
    }
    private Message makeConfirmMessage(Context context,Inventory inventory,Order courierOrder, Order parentOrder){
        Message message = new Message();

        Address from = parentOrder.getFacility();
        Address to = parentOrder.getShipToAddress();

        in.succinct.beckn.Order order = new in.succinct.beckn.Order();
        message.setOrder(order);

        Fulfillment fulfillment = new Fulfillment();
        fulfillment.setStart(makeFulfillmentStop(from));
        fulfillment.setTracking(false);
        fulfillment.setEnd(makeFulfillmentStop(to));
        order.setFulfillment(fulfillment);

        Billing billing = new Billing();
        in.succinct.beckn.Address address = new in.succinct.beckn.Address();
        BillToAddress billToAddress = parentOrder.getBillToAddress();
        billing.setAddress(address);

        order.setBilling(billing);
        order.setQuote(new Quote());
        order.getQuote().setPrice(new Price());
        order.getQuote().getPrice().setValue(courierOrder.getShippingSellingPrice());


        //TOODO FILL
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

        Items items = new Items();
        Item item = new Item();
        String externalItemIdPattern = "/nic2004:55204/(.*)@(.*)\\."+Entity.item ;
        Matcher matcher = Pattern.compile(externalItemIdPattern).matcher(inventory.getExternalSkuId());
        if (matcher.find()){
            item.setId(matcher.group(1));
            item.setQuantity(new Quantity());
            context.setBppId(matcher.group(2));
            JSONObject bpp = getBpp(context.getBppId());
            context.setBppUri((String)bpp.get("subscriber_url"));
            items.add(item);
            order.setItems(items);
        }
        Payment payment = new Payment();
        order.setPayment(payment);
        payment.setParams(new Params());
        payment.getParams().set("currency","INR");
        payment.getParams().set("transaction_id",context.getTransactionId());
        payment.getParams().set("transaction_status","NOT-PAID");
        payment.getParams().set("amount",String.valueOf(courierOrder.getShippingSellingPrice()));
        payment.setType("POST-FULFILLMENT");
        payment.setStatus("NOT-PAID");
        order.setAddOns(new AddOns());
        order.setOffers(new Offers());
        //item.setId(inventory.getExternalSkuId());
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
    public JSONObject getBpp(String bppId){
        JSONObject input = new JSONObject();
        input.put("subscriber_id",bppId);
        input.put("type","BPP");
        input.put("domain","nic2004:55204");
        input.put("country","IND");
        JSONArray out = BecknPublicKeyFinder.lookup(network,input);
        if (out != null && out.size() > 0){
            return (JSONObject) out.get(0);
        }
        return null;
    }

    private List<OnSearch> search(Request request) {
        JSONObject gw = getGateway();
        String authHeader = request.generateAuthorizationHeader(request.getContext().getBapId(),BecknUtil.getCryptoKeyId(network,BecknUtil.LOCAL_DELIVERY));

        MessageCallbackUtil.getInstance().initializeCallBackData(request.getContext().getMessageId(),request.getContext().getTtl());
        try {
            InputStream responseStream = new Call<>().url((String)gw.get("subscriber_url") +"/search").header("content-type","application/json").header("accept","application/json").
                    header("Authorization", authHeader).inputFormat(InputFormat.JSON).input(request.getInner()).method(HttpMethod.POST).getResponseStream();

            Response response = new Response(StringUtil.read(responseStream));
            List<OnSearch> onSearches = new ArrayList<>();
            if (response.getAcknowledgement().getStatus() == Status.ACK) {
                JSONObject aResponse;
                while ((aResponse = MessageCallbackUtil.getInstance().getNextResponse(request.getContext().getMessageId())) != null){
                    onSearches.add(new OnSearch(aResponse));
                }
            }
            return onSearches;
        }finally {
            MessageCallbackUtil.getInstance().shutdownCallBacks(request.getContext().getMessageId());
        }
    }


    @Override
    public List<CourierQuote> getQuotes(Order retailOrder) {
        Request searchRequest = makeSearchJson(retailOrder);
        return getQuotes(searchRequest);
    }

    @Override
    public Order getOrder(JSONObject statusJson) {
        return Order.find(getCourierOrder(statusJson).getOrder().getId());
    }

    @Override
    public CourierOrder getCourierOrder(JSONObject statusJson) {
        OnStatus onStatus = new OnStatus(statusJson);

        return new CourierOrder() {

            @Override
            public in.succinct.beckn.Order getOrder() {
                return onStatus.getMessage().getOrder();
            }

            @Override
            public Context getContext() {
                return onStatus.getContext();
            }
        };
    }

    @Override
    public CourierOrder book(Inventory inventory, Order courierOrder , Order parentOrder) {
        Request confirmRequest = makeConfirmJson(inventory,courierOrder,parentOrder);

        String authHeader = confirmRequest.generateAuthorizationHeader(confirmRequest.getContext().getBapId(),BecknUtil.getCryptoKeyId(network,BecknUtil.LOCAL_DELIVERY));


        MessageCallbackUtil.getInstance().initializeCallBackData(confirmRequest.getContext().getMessageId(),confirmRequest.getContext().getTtl());

        try {
            InputStream responseStream = new Call<>().url(confirmRequest.getContext().getBppUri() +"/confirm").header("content-type","application/json").header("accept","application/json").
                    header("Authorization", authHeader).inputFormat(InputFormat.JSON).input(confirmRequest.getInner()).getResponseStream();

            Response response = new Response(StringUtil.read(responseStream));
            if (response.getAcknowledgement().getStatus() == Status.ACK) {
                    JSONObject aResponse = MessageCallbackUtil.getInstance().getNextResponse(confirmRequest.getContext().getMessageId());
                    OnConfirm onConfirm = new OnConfirm(aResponse);
                    return new CourierOrder() {
                        @Override
                        public in.succinct.beckn.Order getOrder() {
                            return onConfirm.getMessage().getOrder();
                        }

                        @Override
                        public Context getContext() {
                            return onConfirm.getContext();
                        }
                    };
            }
        }finally {
            MessageCallbackUtil.getInstance().shutdownCallBacks(confirmRequest.getContext().getMessageId());
        }

        throw new RuntimeException("Could not book order with partner courier. Please try later.");
    }

    private List<CourierQuote> getQuotes(Request searchRequest){
        List<OnSearch> onSearches = search(searchRequest);

        List<CourierQuote> quotes = new ArrayList<>();
        onSearches.forEach(onSearch-> {
            Providers providers = onSearch.getMessage().getCatalog().getProviders();
            for (Provider provider : providers){
                for (Item item : provider.getItems()){
                    quotes.add(new CourierQuote() {
                        public Descriptor getDescriptor(){
                            return onSearch.getMessage().getCatalog().getDescriptor();
                        }

                        @Override
                        public Item getItem() {
                            return item;
                        }

                        @Override
                        public Provider getProvider() {
                            return provider;
                        }

                        @Override
                        public Context getContext() {
                            return onSearch.getContext();
                        }

                        @Override
                        public Category getCategory() {
                            return provider.getCategories().get(item.getCategoryId());
                        }
                    });
                }
            }
        });
        return quotes;
    }

}
