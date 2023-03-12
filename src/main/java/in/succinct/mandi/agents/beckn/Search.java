package in.succinct.mandi.agents.beckn;

import com.venky.core.math.DoubleHolder;
import com.venky.core.math.DoubleUtils;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.plugins.lucene.index.LuceneIndexer;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.routing.Config;
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
import in.succinct.beckn.Images;
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
import in.succinct.beckn.Tags;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.mandi.util.beckn.OrderUtil;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.participation.Company;
import org.apache.lucene.search.Query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class Search extends BecknAsyncTask {

    public Search(Request request,Map<String,String> headers){
        super(request,headers);
    }
    static final int MAX_LIST_RECORDS = 10;

    private StringBuilder q(String name, String value){
        StringBuilder q = new StringBuilder();
        if (ObjectUtil.isVoid(value)){
            return q;
        }
        StringTokenizer tokenizer = new StringTokenizer(value);
        q.append("(");
        while (tokenizer.hasMoreTokens()){
            String token = tokenizer.nextToken();
            q.append("(").append(name).append(":").append(token).append("*)");
            if (tokenizer.hasMoreTokens()){
                q.append(" OR ");
            }
        }
        q.append(")");

        return q;
    }
    @Override
    public Request executeInternal() {
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
        Long providerId = null;
        if (provider != null){
            Descriptor descriptor =provider.getDescriptor();
            if (descriptor != null){
                providerName = descriptor.getName();
            }
            if (provider.getId() != null){
                String id = BecknUtil.getLocalUniqueId(provider.getId(),Entity.provider);
                if (!ObjectUtil.isVoid(id)){
                    providerId = Long.valueOf(id);
                }
            }
        }
        Price price = item != null ? item.getPrice() : null ;
        Fulfillment fulfillment = intent.getFulfillment();
        FulfillmentStop end = fulfillment == null ? null : fulfillment.getEnd();
        GeoCoordinate deliveryLocation = end == null ? null : end.getLocation().getGps();
        double maxDistance = getMaxDistance(end);


        List<Long> facilityIds = getCloseByFacilities(fulfillment,providerName,providerId);
        if (facilityIds != null && facilityIds.isEmpty()){
            List<Inventory> inventories = new ArrayList<>();
            return push_onsearch(inventories);
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
            qryString.append("(").append(q("SKU", itemName)).append(" OR ").append(q("TAGS",itemName)).append(" )");
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
            return push_onsearch(new ArrayList<>());
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
                            if (deliveryRule != null){
                                record.setDeliveryCharges(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance(),false),2).getHeldDouble().doubleValue());
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
        return push_onsearch(inventories);
    }

    private Request push_onsearch(List<Inventory> inventories) {
        Company company = CompanyUtil.getCompany();
        OnSearch onSearch = new OnSearch();
        onSearch.setContext(getRequest().getContext());
        onSearch.setMessage(new Message());
        onSearch.getContext().setAction("on_search");

        Catalog catalog = new Catalog();
        catalog.setId(BecknUtil.getBecknId("",null));
        catalog.setDescriptor(new Descriptor());
        catalog.getDescriptor().setName(company.getName());

        Providers providers = new Providers();
        catalog.setProviders(providers);
        onSearch.getMessage().setCatalog(catalog);

        /* TODO:VENKY Provider Fulfillments can be filled. */


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
                if ( getRequest().getMessage().getIntent().getProvider() != null) {
                    provider.set("matched",true);
                }
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

            String itemId  = BecknUtil.getBecknId(String.valueOf(inv.getId()),Entity.item);
            Item item = new Item();

            Price price = new Price();
            item.setId(itemId);
            item.setDescriptor(new Descriptor());
            item.getDescriptor().setName(sku.getName());
            if (ObjectUtil.isVoid(sku.getShortDescription())){
                item.getDescriptor().setShortDesc(sku.getName());
                item.getDescriptor().setLongDesc(sku.getName());
            }else {
                item.getDescriptor().setShortDesc(sku.getShortDescription());
                item.getDescriptor().setLongDesc(sku.getShortDescription());
            }
            if (!ObjectUtil.isVoid(sku.getLongDescription())){
                item.getDescriptor().setLongDesc(sku.getLongDescription());
            }

            item.setPrice(price);
            item.setLocationId(locationId);
            if (DoubleUtils.compareTo(inv.getMaxRetailPrice(),inv.getSellingPrice(),2)>0){
                price.setOfferedValue(inv.getSellingPrice());
            }
            price.setListedValue(inv.getMaxRetailPrice());
            price.setValue(inv.getSellingPrice());
            price.setCurrency("INR");
            if ( getRequest().getMessage().getIntent().getItem() != null) {
                item.set("matched",true);
            }
            item.setRecommended(true);
            if (!sku.getAttachments().isEmpty()){
                Images images = new Images();
                images.add(Config.instance().getServerBaseUrl() + sku.getAttachments().get(0).getAttachmentUrl());
                item.getDescriptor().setImages(images);
            }
            item.setTags(OrderUtil.getTags(inv));

            provider.getItems().add(item);



        });

        return(onSearch);
    }


    private double getMaxDistance(FulfillmentStop end){
        double radius = 0;
        if (end != null && end.getLocation().getGps() != null){
            Circle circle = end.getLocation().getCircle();
            if (circle != null){
                radius = circle.getDouble("radius");
            }
            if (radius == 0){
                radius = 50.0;
            }
        }
        return radius;
    }

    private List<Long> getCloseByFacilities(Fulfillment fulfillment, String name, Long providerId) {
        ModelReflector<Facility> ref = ModelReflector.instance(Facility.class);
        if (fulfillment != null){
            FulfillmentStop end = fulfillment.getEnd();
            double radius = getMaxDistance(end);
            if (radius > 0){
                BoundingBox bb = new BoundingBox(end.getLocation().getGps(),2,radius);
                Expression where = new Expression(ref.getPool(), Conjunction.AND);
                where.add(new Expression(ref.getPool(),"PUBLISHED", Operator.EQ, true));
                if (!ObjectUtil.isVoid(name)){
                    where.add(new Expression(ref.getPool(), "NAME",Operator.EQ, name));
                }
                if (!ref.isVoid(providerId)){
                    where.add(new Expression(ref.getPool(), "CREATOR_ID",Operator.EQ, providerId));
                }

                Expression or = new Expression(ref.getPool(), Conjunction.OR);
                where.add(or);
                Location location = end.getLocation();
                GeoCoordinate gps = location == null ? null : location.getGps();
                if (gps != null) {
                    Expression deliveryProvidedWhere = new Expression(ref.getPool(),Conjunction.AND);
                    deliveryProvidedWhere.add(new Expression(ref.getPool(),"DELIVERY_RADIUS",Operator.GT, 0));
                    deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LAT",Operator.LE,gps.getLat()));
                    deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LAT",Operator.GE,gps.getLat()));
                    deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LNG",Operator.LE,gps.getLng()));
                    deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LNG",Operator.GE,gps.getLng()));
                    or.add(deliveryProvidedWhere);
                }

                Expression deliveryNotProvidedWhere = bb.getWhereClause(Facility.class);
                or.add(deliveryNotProvidedWhere);

                List<Facility> facilities = new Select().from(Facility.class).where(where).execute();//bb.find(Facility.class,0,where);
                return DataSecurityFilter.getIds(facilities);
            }
        }else if (!ObjectUtil.isVoid(name) || providerId != null ){
            Expression where = new Expression(ref.getPool(), Conjunction.AND);
            where.add(new Expression(ref.getPool(),"PUBLISHED", Operator.EQ, true));
            if (!ObjectUtil.isVoid(name)){
                where.add(new Expression(ref.getPool(), "NAME",Operator.EQ, name));
            }
            if (!ref.isVoid(providerId)){
                where.add(new Expression(ref.getPool(), "CREATOR_ID",Operator.EQ, providerId));
            }
            List<Facility> facilities = new Select().from(Facility.class).where(where).execute();
            return DataSecurityFilter.getIds(facilities);
        }
        return null;
    }
}