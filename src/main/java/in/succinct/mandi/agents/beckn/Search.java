package in.succinct.mandi.agents.beckn;

import com.venky.core.math.DoubleHolder;
import com.venky.core.math.DoubleUtils;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Circle;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Fulfillment;
import in.succinct.beckn.Fulfillment.FulfillmentType;
import in.succinct.beckn.FulfillmentStop;
import in.succinct.beckn.Intent;
import in.succinct.beckn.Item;
import in.succinct.beckn.Items;
import in.succinct.beckn.Location;
import in.succinct.beckn.Locations;
import in.succinct.beckn.Message;
import in.succinct.beckn.OnSearch;
import in.succinct.beckn.Price;
import in.succinct.beckn.Provider;
import in.succinct.beckn.Providers;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.participation.Company;
import org.apache.lucene.search.Query;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Search extends BecknAsyncTask {
    public Search(Request request){
        super(request);
    }
    static final int MAX_LIST_RECORDS = 10;

    @Override
    public void execute() {
        Request request = getRequest();
        String itemName = null;
        String providerName = null;
        Intent intent = request.getMessage().getIntent();
        Item item = intent.getItem();
        final List<Long> deliverySkuIds = AssetCode.getDeliverySkuIds();
        if (item != null) {
            Descriptor descriptor = item.getDescriptor();
            if (descriptor != null) {
                itemName = descriptor.getName();
            }
        }
        Provider provider = intent.getProvider();
        if (provider != null){
            Descriptor descriptor =provider.getDescriptor();
            if (descriptor != null){
                providerName = descriptor.getName();
            }
        }
        Price price = item != null ? item.getPrice() : null ;
        Fulfillment fulfillment = intent.getFulfillment();
        FulfillmentStop end = fulfillment == null ? null : fulfillment.getEnd();
        GeoCoordinate deliveryLocation = end == null ? null : end.getLocation().getGps();
        double maxDistance = getMaxDistance(end);


        List<Long> facilityIds = getCloseByFacilities(fulfillment,providerName);
        if (facilityIds != null && facilityIds.isEmpty()){
            return;
        }

        LuceneIndexer indexer = LuceneIndexer.instance(Inventory.class);
        StringBuilder qryString = new StringBuilder();
        if (providerName != null){
            qryString.append("FACILITY:").append(providerName).append("*");
        }
        if (itemName != null){
            if (qryString.length() > 0){
                qryString.append(" AND ");
            }
            qryString.append("SKU:" ).append(itemName).append("*");
        }
        if (facilityIds != null){
            if (qryString.length() > 0){
                qryString.append(" AND ");
            }
            qryString.append("(");
            for (Iterator<Long> i = facilityIds.iterator(); i.hasNext() ;){
                qryString.append("FACILITY_ID:").append(i.next());
                if (i.hasNext()){
                    qryString.append(" OR ");
                }
            }
            qryString.append(")");
        }
        if (qryString.length() == 0){
            return;
        }
        Query q = indexer.constructQuery(qryString.toString() );
        List<Long> ids = indexer.findIds(q, Select.MAX_RECORDS_ALL_RECORDS);

        ModelReflector<Inventory> inventoryModelReflector = ModelReflector.instance(Inventory.class);
        Expression inventoryWhere = new Expression(inventoryModelReflector.getPool(),"ID", Operator.IN,ids.toArray());

        List<Inventory> inventories = new Select().from(Inventory.class).where(inventoryWhere).execute(Inventory.class,MAX_LIST_RECORDS,
                record -> {
                    Facility facility = record.getFacility().getRawRecord().getAsProxy(Facility.class);
                    boolean pass = facility.isPublished();
                    pass = pass && record.isPublished();
                    pass = pass && ( record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0 );

                    pass = pass && !deliverySkuIds.contains(record.getSkuId());
                    if (pass){
                        double facilityDistance = 0 ;
                        if (deliveryLocation != null){
                            facilityDistance = new GeoCoordinate(facility).distanceTo(deliveryLocation);
                        }
                        facility.setDistance(facilityDistance);
                        record.setDeliveryProvided(facility.isDeliveryProvided() && facility.getDeliveryRadius() > facility.getDistance());
                        if (record.isDeliveryProvided()){
                            Inventory deliveryRule = facility.getDeliveryRule(false);
                            if (deliveryRule == null || ObjectUtil.isVoid(deliveryRule.getManagedBy())){
                                record.setDeliveryCharges(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance()),2).getHeldDouble().doubleValue());
                            }
                            record.setChargeableDistance(new DoubleHolder(facility.getDistance(),2).getHeldDouble().doubleValue());
                        }
                    }
                    if (fulfillment != null) {
                        if (fulfillment.getType() == FulfillmentType.store_pickup){
                            pass = pass && facility.getDistance() <= maxDistance;
                        }else if (fulfillment.getType() == FulfillmentType.home_delivery){
                            pass = pass && record.isDeliveryProvided() ;
                        }
                    }
                    pass = pass && (record.isDeliveryProvided() || facility.getDistance() <= maxDistance);
                    pass =  pass && (!record.isDeliveryProvided() || (record.getDeliveryCharges() != null && !record.getDeliveryCharges().isInfinite()));
                    if (price != null){
                        pass = pass && ( price.getMaximumValue() == 0.0D || price.getMaximumValue() >= record.getSellingPrice());
                        pass = pass && ( price.getMinimumValue() == 0.0D || price.getMinimumValue() <= record.getSellingPrice());
                    }
                    return pass;
                });
        push_onsearch(inventories);
    }

    private void push_onsearch(List<Inventory> inventories) {
        Company company = CompanyUtil.getCompany();
        OnSearch onSearch = new OnSearch();
        onSearch.setContext(getRequest().getContext());
        onSearch.setMessage(new Message());

        Catalog catalog = new Catalog();
        catalog.setId(BecknUtil.getBecknId("",null));
        catalog.setDescriptor(new Descriptor());
        catalog.getDescriptor().setName(company.getName());

        Providers providers = new Providers();
        catalog.setProviders(providers);
        onSearch.getMessage().setCatalog(catalog);


        inventories.forEach(inv->{
            String providerId = BecknUtil.getBecknId(String.valueOf(inv.getFacility().getCreatorUserId()),Entity.provider);
            Facility facility = inv.getFacility().getRawRecord().getAsProxy(Facility.class);
            Provider provider = providers.get(providerId);
            if (provider == null){
                provider = new Provider();
                provider.setId(providerId);
                provider.setDescriptor(new Descriptor());
                provider.getDescriptor().setName(facility.getName());
                provider.setLocations(new Locations());
                provider.setItems(new Items());
                provider.set("matched",true);
                providers.add(provider);
            }
            String locationId = BecknUtil.getBecknId(String.valueOf(inv.getFacilityId()),Entity.provider_location);
            if (provider.getLocations().get(locationId) == null){
                Location location = new Location();
                location.setId(locationId);
                location.setGps(new GeoCoordinate(inv.getFacility()));
                provider.getLocations().add(location);
            }
            Sku sku = inv.getSku().getRawRecord().getAsProxy(Sku.class);

            String itemId  = BecknUtil.getBecknId(String.valueOf(inv.getSkuId()),Entity.item);
            Item item = new Item();

            Price price = new Price();
            item.setId(itemId);
            item.setDescriptor(new Descriptor());
            item.getDescriptor().setName(sku.getName());
            item.setPrice(price);
            item.setLocationId(locationId);
            if (DoubleUtils.compareTo(inv.getMaxRetailPrice(),inv.getSellingPrice(),2)>0){
                price.setOfferedValue(inv.getSellingPrice());
            }
            price.setListedValue(inv.getMaxRetailPrice());
            price.setValue(inv.getSellingPrice());
            price.setCurrency("INR");
            item.set("matched",true);
            item.setRecommended(true);
            provider.getItems().add(item);

        });

        new Call<JSONObject>().url(getRequest().getContext().getBapUri() + "/on_search").method(HttpMethod.POST).inputFormat(InputFormat.JSON).
                input(onSearch.getInner()).headers(getHeaders(onSearch)).getResponseAsJson();
    }

    private Map<String, String> getHeaders(OnSearch onSearch) {
        Map<String,String> headers  = new HashMap<>();
        headers.put("Authorization",onSearch.generateAuthorizationHeader(onSearch.getContext().getBppId(),onSearch.getContext().getBppId() + ".k1"));
        headers.put("Content-Type", MimeType.APPLICATION_JSON.toString());
        headers.put("Accept", MimeType.APPLICATION_JSON.toString());

        return headers;
    }

    private double getMaxDistance(FulfillmentStop end){
        double radius = 0;
        if (end != null && end.getLocation().getGps() != null){
            Circle circle = end.getLocation().getCircle();
            if (circle != null){
                radius = circle.getDouble("radius");
            }
            if (radius == 0){
                radius = 5.0;
            }
        }
        return radius;
    }

    private List<Long> getCloseByFacilities(Fulfillment fulfillment, String name) {
        if (fulfillment != null){
            FulfillmentStop end = fulfillment.getEnd();
            if (end != null){
                GeoCoordinate deliveryLocation = end.getLocation().getGps();
                Circle circle = end.getLocation().getCircle();
                double radius = 0;
                if (circle != null) {
                    radius = circle.getDouble("radius");
                    if (radius == 0) {
                        radius = 5;
                    }

                }
                BoundingBox bb = new BoundingBox(deliveryLocation,2,radius);
                ModelReflector<Facility> ref = ModelReflector.instance(Facility.class);
                Expression where = new Expression(ref.getPool(), Conjunction.AND);
                where.add(new Expression(ref.getPool(),"PUBLISHED", Operator.EQ, true));
                if (!ObjectUtil.isVoid(name)){
                    where.add(new Expression(ref.getPool(), "NAME",Operator.EQ, name));
                }
                List<Facility> facilities = bb.find(Facility.class,0,where);
                return DataSecurityFilter.getIds(facilities);
            }
        }
        return null;
    }
}