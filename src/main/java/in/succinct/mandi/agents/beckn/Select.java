package in.succinct.mandi.agents.beckn;


import com.venky.core.math.DoubleUtils;
import com.venky.core.util.Bucket;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.beckn.BreakUp;
import in.succinct.beckn.BreakUp.BreakUpElement;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnSelect;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Quantity;
import in.succinct.beckn.Quote;
import in.succinct.beckn.Request;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Select extends BecknAsyncTask {
    public Select(Request request){
        super(request);
    }
    @Override
    public void execute() {
        Request request = getRequest();
        JSONObject selected = request.getMessage().get("selected");

        Provider provider = new Provider();
        provider.setInner((JSONObject) selected.get("provider"));

        Location location = provider.getLocations().get(0);
        Long facilityId = Long.valueOf(BecknUtil.getLocalUniqueId(String.valueOf(location.getId()),Entity.provider_location));

        Items items = new Items();
        items.setInner((JSONArray) selected.get("items"));

        Bucket itemPrice = new Bucket();
        Bucket listedPrice = new Bucket();

        for (int i = 0 ; i < items.size() ; i ++ ){
            Item item = items.get(i);
            Long skuId = Long.valueOf(BecknUtil.getLocalUniqueId(item.getId(), Entity.item));
            Quantity quantity = item.get(Quantity.class,"quantity");

            Inventory inventory = Inventory.find(facilityId,skuId);
            itemPrice.increment(inventory.getSellingPrice() * quantity.getMeasure());
            listedPrice.increment(inventory.getMaxRetailPrice() * quantity.getMeasure());
        }

        OnSelect onSelect = new OnSelect();
        onSelect.setContext(request.getContext());
        Message message = new Message();
        onSelect.setMessage(message);
        Quote quote = new Quote();
        message.setQuote(quote);
        Price price = new Price();
        quote.setPrice(price);
        price.setListedValue(listedPrice.value());
        price.setCurrency("INR");
        if (DoubleUtils.compareTo(listedPrice.value(), itemPrice.value() ,2)>0){
            price.setOfferedValue(itemPrice.value());
        }
        quote.setTtl(15L*60L); //15 minutes.
        BreakUp breakUp = new BreakUp();
        BreakUpElement element = breakUp.createElement("item","Total Product",price);
        breakUp.add(element);
        quote.setBreakUp(breakUp);


        new Call<JSONObject>().url(getRequest().getContext().getBapUri() + "/on_select").
                method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(onSelect.getInner()).headers(getHeaders(onSelect)).getResponseAsJson();

    }

    private Map<String, String> getHeaders(OnSelect onSelect) {
        Map<String,String> headers  = new HashMap<>();
        headers.put("Authorization",onSelect.generateAuthorizationHeader(onSelect.getContext().getBppId(),onSelect.getContext().getBppId() + ".k1"));
        return headers;
    }
}
