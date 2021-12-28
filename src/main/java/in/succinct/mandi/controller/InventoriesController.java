package in.succinct.mandi.controller;

import com.venky.core.math.DoubleHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.network.Network;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.sql.Select.ResultFilter;
import com.venky.swf.views.View;
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
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InventoriesController extends ModelController<Inventory> {
    public InventoriesController(Path path) {
        super(path);
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map =  super.getIncludedModelFields();
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE","LAT","LNG","DELIVERY_PROVIDED","COD_ENABLED","DELIVERY_RADIUS","MIN_CHARGEABLE_DISTANCE","MIN_DELIVERY_CHARGE"));
        {
            List<String> itemFields = ModelReflector.instance(Item.class).getUniqueFields();
            itemFields.add("ASSET_CODE_ID");
            itemFields.add("ID");

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
    protected int getMaxDistance(){
        return Math.max(5,getReflector().getJdbcTypeHelper().getTypeRef(Integer.class).getTypeConverter().
                valueOf(getPath().getFormFields().get("MaxDistance")));
    }

    public long getOrderId(){
        return getReflector().getJdbcTypeHelper().getTypeRef(Long.class).getTypeConverter().valueOf(getPath().getFormFields().get("OrderId"));
    }
    public Order getOrder(){
        long orderId = getOrderId();
        if (orderId > 0){
            return Database.getTable(Order.class).get(orderId);
        }
        return null;
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
                record.setDeliveryCharges(quote.getItem().getPrice().getEstimatedValue());
                record.setDeliveryProvided(true);
                record.setExternal(true);
                record.setTags(quote.getItem().getDescriptor().getName()+", "+quote.getProvider().getDescriptor().getName());
                record.setNetworkId(network.getRegistryId());
                record.setMaxRetailPrice(0.0D);
                record.setSellingPrice(0.0D);


                String itemId = BecknUtil.getBecknId("/nic2004:55204/",quote.getItem().getId(), "@" + quote.getContext().getBppId(),Entity.item);

                record.setExternalSkuId(itemId);
                record.setExternalFacilityId(BecknUtil.getBecknId("/nic2004:55204/",quote.getProvider().getId(), "@" + quote.getContext().getBppId(),Entity.provider));
                record.setSkuId(deliverySkuIds.get(0)) ; //May need to fine tune this TODO VENKY
                records.add(record);

            }
        }
    }


    List<Long> deliverySkuIds = AssetCode.getDeliverySkuIds();
    boolean HBO_SUBSCRIPTION_ITEM_PRESENT = CompanyUtil.isHumBhiOnlineSubscriptionItemPresent();
    @Override
    protected ResultFilter<Inventory> getFilter() {
        final ResultFilter<Inventory> superFilter = super.getFilter();

        return record -> {
            Facility facility = record.getFacility().getRawRecord().getAsProxy(Facility.class);
            Order order = getOrder();


            boolean pass = facility.isPublished();
            pass = pass && record.isPublished();
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
                    record.setDeliveryCharges(facility.getDeliveryCharges(distanceBetweenPickUpAndDeliveryLocation));
                    record.setChargeableDistance(Math.max(facility.getMinChargeableDistance(),new DoubleHolder(distanceBetweenPickUpAndDeliveryLocation,2).getHeldDouble().doubleValue()));
                    if (deliveryBoyLocation != null) {
                        facility.setDistance(new DoubleHolder(new GeoCoordinate(deliveryBoyLocation).distanceTo(new GeoCoordinate(order.getFacility())), 2).getHeldDouble().doubleValue());
                    }else {
                        facility.setDistance(0.0);
                    }
                }
            }else {
                //Do not return Delivery items.
                pass = pass && !deliverySkuIds.contains(record.getSkuId());
                if (pass){
                    record.setDeliveryProvided(facility.isDeliveryProvided() && facility.getDeliveryRadius() > facility.getDistance());
                    if (record.isDeliveryProvided()){
                        Inventory deliveryRule = facility.getDeliveryRule(false);
                        record.setDeliveryCharges(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance()),2).getHeldDouble().doubleValue());
                        record.setChargeableDistance(new DoubleHolder(facility.getDistance(),2).getHeldDouble().doubleValue());
                    }
                }
            }
            pass =  pass && (!record.isDeliveryProvided() || (record.getDeliveryCharges() != null && !record.getDeliveryCharges().isInfinite()));
            pass = pass && (record.isDeliveryProvided() || facility.getDistance() < getMaxDistance());
            pass = pass &&  superFilter.pass(record);

            return pass;
        };

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
    protected Expression getWhereClause() {
        Expression where = super.getWhereClause();
        int maxDistance = getMaxDistance();
        if (maxDistance <= 10){
            Order order = getOrder();
            GeoCoordinate reference;
            if (order == null){
                reference = getCurrentUserLocation();
            }else {
                reference = new GeoCoordinate(order.getFacility());
            }
            if (reference != null){
                ModelReflector<Facility> ref = ModelReflector.instance(Facility.class);
                Expression fWhere = new Expression(ref.getPool(),Conjunction.AND);
                fWhere.add(new Expression(ref.getPool(),"PUBLISHED",Operator.EQ,true));
                Expression or = new Expression(ref.getPool(), Conjunction.OR);
                fWhere.add(or);

                Expression deliveryProvidedWhere = new Expression(ref.getPool(),Conjunction.AND);
                deliveryProvidedWhere.add(new Expression(ref.getPool(),"DELIVERY_RADIUS",Operator.GT, 0));
                deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LAT",Operator.LE,reference.getLat()));
                deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LAT",Operator.GE,reference.getLat()));
                deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LNG",Operator.LE,reference.getLng()));
                deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LNG",Operator.GE,reference.getLng()));
                or.add(deliveryProvidedWhere);

                BoundingBox bb = new BoundingBox(new GeoCoordinate(reference),0,maxDistance);
                Expression deliveryNotProvidedWhere = bb.getWhereClause(Facility.class);
                or.add(deliveryNotProvidedWhere);
                List<Facility> facilities = new Select().from(Facility.class).where(fWhere).execute();
                if (!facilities.isEmpty()){
                    where.add(Expression.createExpression(getReflector().getPool(),"FACILITY_ID", Operator.IN, DataSecurityFilter.getIds(facilities).toArray()));
                }else {
                    where.add(new Expression(getReflector().getPool(), "ID",Operator.EQ, -1)); //Impossible!!
                }
            }
        }
        return where;
    }
    private User getCurrentUser(){
        com.venky.swf.db.model.User currentUser = Database.getInstance().getCurrentUser();
        if (currentUser != null){
            return currentUser.getRawRecord().getAsProxy(User.class);
        }
        return null;
    }
    private GeoCoordinate getCurrentUserLocation() {
        com.venky.swf.db.model.User currentUser = Database.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getCurrentLat() != null){
            return new GeoCoordinate(new GeoLocation() {
                @Override
                public BigDecimal getLat() {
                    return currentUser.getCurrentLat();
                }

                @Override
                public void setLat(BigDecimal bigDecimal) {
                    currentUser.setCurrentLat(bigDecimal);
                }

                @Override
                public BigDecimal getLng() {
                    return currentUser.getCurrentLng();
                }

                @Override
                public void setLng(BigDecimal bigDecimal) {
                    currentUser.setCurrentLng(bigDecimal);
                }
            });
        }
        return null;
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
