package in.succinct.mandi.controller;

import com.venky.core.math.DoubleHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select.ResultFilter;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.Sku;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.UserLocation;
import in.succinct.mandi.integrations.courier.Wefast;
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
        map.put(Facility.class, Arrays.asList("ID","NAME","DISTANCE","LAT","LNG","DELIVERY_PROVIDED","DELIVERY_RADIUS","FIXED_DELIVERY_CHARGES","MIN_FIXED_DISTANCE"));
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

    List<Long> deliverySkuIds = AssetCode.getDeliverySkuIds();
    @Override
    protected ResultFilter<Inventory> getFilter() {
        final ResultFilter<Inventory> superFilter = super.getFilter();

        return record -> {
            Facility facility = record.getFacility().getRawRecord().getAsProxy(Facility.class);
            Order order = getOrder();

            boolean pass = facility.isPublished();
            pass = pass && (record.isInfinite() || record.getQuantity() > 0);
            pass = pass && ( record.getFacility().getCreatorUser().getRawRecord().getAsProxy(User.class).getBalanceOrderLineCount() > 0
                    || record.getSku().getItem().getRawRecord().getAsProxy(Item.class).isHumBhiOnlineSubscriptionItem() ||
                    !ObjectUtil.isVoid(record.getManagedBy()));


            if (order != null) {
                //Return only delivery items
                //
                GeoLocation deliveryBoyLocation = getDeliveryBoyLocation(facility,record);

                double distanceToDeliveryLocation = 0;
                double distanceBetweenPickUpAndDeliveryLocation = 0 ;
                double distanceToPickLocation = 0 ;
                GeoCoordinate shipTo = null;

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
                    if (ObjectUtil.isVoid(record.getManagedBy())){
                        record.setDeliveryCharges(facility.getDeliveryCharges(distanceBetweenPickUpAndDeliveryLocation));
                    }else if (ObjectUtil.equals(record.getManagedBy(),"wefast")){
                        Wefast wefast = new Wefast();
                        record.setDeliveryCharges(wefast.getPrice(wefast.getPrice(order)));
                    }
                    record.setChargeableDistance(Math.max(facility.getMinFixedDistance(),new DoubleHolder(distanceBetweenPickUpAndDeliveryLocation,2).getHeldDouble().doubleValue()));
                    facility.setDistance(new DoubleHolder(new GeoCoordinate(deliveryBoyLocation).distanceTo(new GeoCoordinate(order.getFacility())),2).getHeldDouble().doubleValue());
                }
            }else {
                //Dont return Delivery items.
                pass = pass && !deliverySkuIds.contains(record.getSkuId());
                if (pass){
                    record.setDeliveryProvided(facility.isDeliveryProvided() && facility.getDeliveryRadius() > facility.getDistance());
                    if (record.isDeliveryProvided()){
                        Inventory deliveryRule = facility.getDeliveryRule();
                        if (deliveryRule == null || ObjectUtil.isVoid(deliveryRule.getManagedBy())){
                            record.setDeliveryCharges(new DoubleHolder(facility.getDeliveryCharges(facility.getDistance()),2).getHeldDouble().doubleValue());
                        }else if (ObjectUtil.equals(deliveryRule.getManagedBy(),"wefast")){
                            Wefast wefast = new Wefast();
                            record.setDeliveryCharges(wefast.getPrice(wefast.getPrice(facility,getCurrentUser(),record)));
                        }else {
                            throw new RuntimeException("Unknown courier:" + deliveryRule.getManagedBy());
                        }
                        record.setChargeableDistance(new DoubleHolder(facility.getDistance(),2).getHeldDouble().doubleValue());
                    }
                }
            }
            pass = pass && facility.getDistance() < getMaxDistance() ;
            pass = pass &&  superFilter.pass(record);

            return pass;
        };

    }

    private GeoLocation getDeliveryBoyLocation(Facility facility, Inventory record) {
        if (!ObjectUtil.isVoid(record.getManagedBy())){
            return facility;
        }else {
            User creator = facility.getCreatorUser().getRawRecord().getAsProxy(User.class);
            if (!creator.getUserLocations().isEmpty()){
                UserLocation location = creator.getUserLocations().get(0);
                return location;
            }else {
                return facility;
            }
        }
    }

    @Override
    protected Expression getWhereClause() {
        Expression where = super.getWhereClause();
        int maxDistance = getMaxDistance();
        if (maxDistance <= 10){
            Order order = getOrder();
            GeoCoordinate reference = null;
            if (order == null){
                reference = getCurrentUserLocation();
            }else {
                reference = new GeoCoordinate(order.getFacility());
            }
            if (reference != null){
                BoundingBox bb = new BoundingBox(reference,2,maxDistance);

                List<Facility> facilities  = bb.find(Facility.class,getMaxListRecords());
                if (!facilities.isEmpty()){
                    where.add(new Expression(getReflector().getPool(),"FACILITY_ID", Operator.IN, DataSecurityFilter.getIds(facilities).toArray()));
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