package in.succinct.mandi.controller;

import com.venky.core.math.DoubleHolder;
import com.venky.core.math.DoubleUtils;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Select.ResultFilter;
import com.venky.swf.views.View;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Images;
import in.succinct.beckn.Provider;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.integrations.courier.CourierAggregator;
import in.succinct.mandi.integrations.courier.CourierAggregator.CourierQuote;
import in.succinct.mandi.integrations.courier.CourierAggregatorFactory;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;
import in.succinct.plugins.ecommerce.db.model.attachments.Attachment;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCodeAttribute;
import in.succinct.plugins.ecommerce.db.model.attributes.Attribute;
import in.succinct.plugins.ecommerce.db.model.attributes.AttributeValue;
import in.succinct.plugins.ecommerce.db.model.catalog.ItemAttributeValue;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoriesController extends LocalSearchController<Inventory> {
    public InventoriesController(Path path) {
        super(path);
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE","LAT","LNG", "PUBLISHED","DELIVERY_PROVIDED","DELIVERY_CHARGED_ON_ACTUAL", "COD_ENABLED","DELIVERY_RADIUS","MIN_CHARGEABLE_DISTANCE","MIN_DELIVERY_CHARGE", "DELIVERY_CHARGES" ));
        {
            List<String> itemFields = ModelReflector.instance(Item.class).getUniqueFields();
            itemFields.add("ASSET_CODE_ID");
            itemFields.add("ID");
            itemFields.add("HUM_BHI_ONLINE_SUBSCRIPTION_ITEM");

            map.put(Item.class, itemFields);
        }
        map.put(Attachment.class,Arrays.asList("ID","ATTACHMENT_URL"));
        return map;
    }

    @Override
    protected Map<Class<? extends Model>, List<Class<? extends Model>>> getConsideredChildModels() {
        Map<Class<? extends Model>, List<Class<? extends Model>>> cache =  super.getConsideredChildModels();
        cache.get(Facility.class).add(Attachment.class);
        cache.get(Sku.class).add(Attachment.class);
        return cache;
    }


    @Override
    protected <T> View list(List<Inventory> records, boolean isCompleteList, IntegrationAdaptor<Inventory, T> overrideIntegrationAdaptor) {
        Order retailOrder = getOrder();
        if (retailOrder  != null){
            addExternalDeliveryInventory(records);
        }else {
            addExternalInventory(records);
        }
        return super.list(records, isCompleteList, overrideIntegrationAdaptor);
    }

    private void addExternalInventory(List<Inventory> records) {
    }

    private void addExternalDeliveryInventory(List<Inventory> records) {
        Order retailOrder = getOrder();
        for (BecknNetwork network : BecknNetwork.all()){
            CourierAggregator aggregator = CourierAggregatorFactory.getInstance().getCourierAggregator(network);
            List<CourierQuote> quotes = aggregator.getQuotes(retailOrder);
            for (CourierQuote quote : quotes){
                Inventory record = Database.getTable(Inventory.class).newRecord();
                ///record.
                //Quantity is number of persons to do the job..
                record.setInfinite(true);
                record.setFacilityId(retailOrder.getFacilityId());
                record.setChargeableDistance(retailOrder.getFacility().getDistance());
                double charges = quote.getItem().getPrice().getValue();
                if (DoubleUtils.equals(0,charges,2)){
                    charges = quote.getItem().getPrice().getEstimatedValue();
                }
                record.setDeliveryCharges(charges);
                record.setDeliveryProvided(true);
                record.setExternal(true);
                record.setTags(quote.getItem().getDescriptor().getName()+", "+quote.getProvider().getDescriptor().getName());
                record.setNetworkId(network.getNetworkId());
                record.setMaxRetailPrice(0.0D);
                record.setSellingPrice(0.0D);


                String itemId = BecknUtil.getBecknId("/nic2004:55204/",quote.getItem().getId(),  quote.getContext().getBppId(),Entity.item);

                record.setExternalSkuId(itemId);
                record.setExternalFacilityId(BecknUtil.getBecknId("/nic2004:55204/",quote.getProvider().getId(), quote.getContext().getBppId(),Entity.provider));
                Sku sku = createDeliverySku(quote);
                record.setSkuId(sku.getId()) ;
                records.add(record);

            }
        }
    }

    final String SERVICE_PROVIDER_LITERAL = "Service Provider";
    final String SERVICE_OPTION_LITERAL = "Service Option";

    private AssetCode createDeliveryAssetCode(){
        AssetCode assetCode = Database.getTable(AssetCode.class).newRecord();
        assetCode.setCode("996813");
        assetCode.setDescription("Local Delivery");
        assetCode.setGstPct(18.0);
        assetCode = Database.getTable(AssetCode.class).getRefreshed(assetCode);
        if (assetCode.getRawRecord().isNewRecord()) {
            assetCode.save();
        }
        return assetCode;
    }
    private Map<String, AttributeValue> registerAttributeValues(Map<String, String> anItemAttributeValueMap) {
        Map<String,AttributeValue> map = new HashMap<>();
        for (String name : anItemAttributeValueMap.keySet()){
            String value = anItemAttributeValueMap.get(name);
            Attribute attribute = Attribute.find(name);

            AttributeValue attributeValue = Database.getTable(AttributeValue.class).newRecord();
            attributeValue.setAttributeId(attribute.getId());
            attributeValue.setPossibleValue(value);
            attributeValue = Database.getTable(AttributeValue.class).getRefreshed(attributeValue);
            if (attributeValue.getRawRecord().isNewRecord()){
                attributeValue.save();
            }
            map.put(name,attributeValue);
        }
        return map;
    }

    private void registerItemAttributeValues(Item item, Map<String,AttributeValue> anItemAttributeValueMap) {
        AssetCode assetCode = item.getAssetCode();

        for (String name : anItemAttributeValueMap.keySet()){
            AttributeValue attributeValue = anItemAttributeValueMap.get(name);

            AssetCodeAttribute assetCodeAttribute = Database.getTable(AssetCodeAttribute.class).newRecord();
            assetCodeAttribute.setAttributeType(AssetCodeAttribute.ATTRIBUTE_TYPE_CATALOG);
            assetCodeAttribute.setAssetCodeId(assetCode.getId());
            assetCodeAttribute.setAttributeId(attributeValue.getAttributeId());
            assetCodeAttribute = Database.getTable(AssetCodeAttribute.class).getRefreshed(assetCodeAttribute);
            if(assetCodeAttribute.getRawRecord().isNewRecord()){
                assetCodeAttribute.save();
            }

            ItemAttributeValue itemAttributeValue = Database.getTable(ItemAttributeValue.class).newRecord();
            itemAttributeValue.setItemId(item.getId());
            itemAttributeValue.setAttributeValueId(attributeValue.getId());
            itemAttributeValue.save();
        }
    }
    private Sku createDeliverySku(CourierQuote quote){
        AssetCode deliveryAssetCode = createDeliveryAssetCode();

        in.succinct.beckn.Item qi = quote.getItem();
        Provider qp = quote.getProvider();

        Map<String,String> itemAttributeValueMap = new HashMap<String,String>(){{
            put(SERVICE_OPTION_LITERAL,BecknUtil.getBecknId("/nic2004:55204/",qi.getId(),  quote.getContext().getBppId(),Entity.item));
            put(SERVICE_PROVIDER_LITERAL,BecknUtil.getBecknId("/nic2004:55204/",qp.getId(), quote.getContext().getBppId(),Entity.provider));
        }};

        Map<String,AttributeValue> map = registerAttributeValues(itemAttributeValueMap);


        Item item = Database.getTable(Item.class).newRecord();
        item.setName(qp.getDescriptor().getName() + " - " + qi.getDescriptor().getName());
        item.setCompanyId(CompanyUtil.getCompanyId());
        item.setTags(qi.getDescriptor().getName()  + "," + qp.getDescriptor().getName());
        item.setAssetCodeId(deliveryAssetCode.getId());
        item.setItemHash(in.succinct.plugins.ecommerce.db.model.catalog.Item.hash(deliveryAssetCode,map.values()));
        item.setItemRestrictedToSingleSeller(true);
        item = Database.getTable(Item.class).getRefreshed(item);
        if (item.getRawRecord().isNewRecord()) {
            item.save();
            registerItemAttributeValues(item, map);
        }

        Sku sku = Database.getTable(Sku.class).newRecord();
        sku.setItemId(item.getId());
        sku.setCompanyId(item.getCompanyId());
        sku.setPackagingUOMId(UnitOfMeasure.getMeasure(UnitOfMeasure.MEASURES_PACKAGING,"Kms").getId());
        sku.setName(item.getName() +"-Kms");
        sku = Database.getTable(Sku.class).getRefreshed(sku);
        sku.setPublished(true);
        if (sku.getRawRecord().isNewRecord()) {
            sku.save();
        }
        if (sku.getAttachments().isEmpty()){
            Descriptor descriptor = quote.getDescriptor();

            Images images = descriptor == null ? null: descriptor.getImages();
            if (images != null && images.size() > 0){
                String url = images.get(0).getUrl();

                Attachment attachment = Database.getTable(Attachment.class).newRecord();
                attachment.setSkuId(sku.getId());
                attachment.setUploadUrl(url);
                attachment.save();
            }
        }



        return sku;
    }



    List<Long> deliverySkuIds = AssetCode.getDeliverySkuIds();
    boolean HBO_SUBSCRIPTION_ITEM_PRESENT = CompanyUtil.isHumBhiOnlineSubscriptionItemPresent();
    @Override
    protected ResultFilter<Inventory> getFilter() {
        final ResultFilter<Inventory> superFilter = super.getFilter();
        User user = getCurrentUser();
        final List<Long> operatingFacilityIds =  user == null ? new ArrayList<>() : user.getOperatingFacilityIds();
        return record -> {
            Facility facility = record.getFacility().getRawRecord().getAsProxy(Facility.class);
            Order order = getOrder();
            boolean myFacility =  operatingFacilityIds.contains(record.getFacilityId()) || ( user != null && (user.isStaff() || user.isAdmin()));

            boolean pass = facility.isPublished();
            //pass = pass && record.isPublished(); (Show as out of stock)
            pass = pass && ( record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0
                    || record.getSku().getItem().getRawRecord().getAsProxy(Item.class).isHumBhiOnlineSubscriptionItem());

            if (order != null) {
                //Return only delivery items
                //
                GeoLocation deliveryBoyLocation = getDeliveryBoyLocation(facility,record);

                double distanceToDeliveryLocation ;
                double distanceBetweenPickUpAndDeliveryLocation = 0;
                double distanceToPickLocation ;
                GeoCoordinate shipTo ;

                pass = pass && facility.isDeliveryProvided() && deliverySkuIds.contains(record.getSkuId());
                if (pass){
                    for (OrderAddress address : order.getAddresses()) {
                        if (ObjectUtil.equals(OrderAddress.ADDRESS_TYPE_SHIP_TO, address.getAddressType())) {
                            shipTo = new GeoCoordinate(address);
                            distanceToDeliveryLocation = shipTo.distanceTo(new GeoCoordinate(facility));
                            distanceBetweenPickUpAndDeliveryLocation = shipTo.distanceTo(new GeoCoordinate(order.getFacility()));
                            pass = pass &&  distanceToDeliveryLocation < facility.getDeliveryRadius();
                        }
                    }
                }
                distanceToPickLocation = new GeoCoordinate(facility).distanceTo(new GeoCoordinate(order.getFacility()));
                pass = pass && distanceToPickLocation < facility.getDeliveryRadius();
                if (pass){
                    record.setDeliveryProvided(true);
                    double deliveryCharges = facility.getDeliveryCharges(distanceBetweenPickUpAndDeliveryLocation,record);

                    record.setMaxRetailPrice(0.0);
                    record.setSellingPrice(0.0);
                    record.setDeliveryCharges(deliveryCharges);
                    record.setChargeableDistance(Math.max(facility.getMinChargeableDistance(),new DoubleHolder(distanceBetweenPickUpAndDeliveryLocation,2).getHeldDouble().doubleValue()));
                    if (deliveryBoyLocation != null) {
                        facility.setDistance(new DoubleHolder(new GeoCoordinate(deliveryBoyLocation).distanceTo(new GeoCoordinate(order.getFacility())), 2).getHeldDouble().doubleValue());
                    }else {
                        facility.setDistance(0.0);
                    }
                }
            }else if (myFacility) {
                pass = superFilter.pass(record);
                if (pass){
                    setDeliveryProvided(record,facility);
                }
                return pass;
            }else {
                //Do not return Delivery items.
                pass = pass && !deliverySkuIds.contains(record.getSkuId());
                if (pass){
                    setDeliveryProvided(record,facility);
                }
            }
            pass =  pass && (!record.isDeliveryProvided() || (record.getDeliveryCharges() != null && !record.getDeliveryCharges().isInfinite()));
            pass = pass && (record.isDeliveryProvided() || facility.getDistance() < getMaxDistance());
            pass = pass &&  superFilter.pass(record);

            return pass;
        };

    }

    protected void setDeliveryProvided(Inventory record,Facility facility){
        record.setDeliveryProvided(facility.isDeliveryProvided() && facility.getDeliveryRadius() > facility.getDistance());
        if (record.isDeliveryProvided()){
            Inventory deliveryRule = facility.getDeliveryRule(false);
            record.setDeliveryCharges(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance(),false),2).getHeldDouble().doubleValue());
            record.setChargeableDistance(new DoubleHolder(facility.getDistance(),2).getHeldDouble().doubleValue());
        }

    }

    protected Expression getWhereClause(){
        return getWhereClause("FACILITY_ID");
    }
    private GeoLocation getDeliveryBoyLocation(Facility facility, Inventory record) {
        User creator = facility.getCreatorUser().getRawRecord().getAsProxy(User.class);
        if (!creator.getUserLocations().isEmpty()){
            return creator.getUserLocations().get(0);
        }else {
            return facility;
        }
    }

    @Override
    @RequireLogin(false)
    public View search() {
        return super.search();
    }

    @Override
    @RequireLogin(false)
    public View search(String strQuery) {
        return super.search(strQuery);
    }

    @Override
    @RequireLogin(false)
    public View index() {
        return super.index();
    }
}
