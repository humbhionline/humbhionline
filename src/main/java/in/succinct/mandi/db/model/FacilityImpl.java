package in.succinct.mandi.db.model;

import com.venky.core.collections.SequenceSet;
import com.venky.core.math.DoubleHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Count;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import org.json.simple.JSONObject;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class FacilityImpl extends ModelImpl<Facility> {
    public FacilityImpl() {
    }

    public FacilityImpl(Facility proxy) {
        super(proxy);
    }

    public Facility getSelfFacility() {
        return getProxy();
    }
    public void publish(){
        Facility f = getProxy();
        f.setPublished(true);
        f.save();
    }

    public void unpublish(){
        Facility facility = getProxy();
        facility.setPublished(false);
        facility.save();
    }

    Double distance = null;
    public Double getDistance() {
        if (distance != null){
            return distance;
        }
        Facility facility = getProxy();
        if (facility.getLat() == null ){
            distance = 0.0;
        }else {
            com.venky.swf.db.model.User u = Database.getInstance().getCurrentUser();
            User currentUser = u == null ? null : u instanceof User ? (User)u : u.getRawRecord().getAsProxy(User.class);
            if (currentUser != null && currentUser.getCurrentLat() != null) {
                distance = new GeoCoordinate(facility).distanceTo(new GeoCoordinate(new GeoLocation() {
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
                }));
            } else {
                distance = 0.0D;
            }
        }
        return distance;

    }
    public void setDistance(Double distance){
        this.distance = distance;
    }

    boolean atLocation  = false;
    public boolean isCurrentlyAtLocation() {
        return atLocation;
    }
    public void setCurrentlyAtLocation(boolean currentlyAtLocation){
        this.atLocation = currentlyAtLocation;
    }

    public Inventory getDeliveryRule(boolean published){
        Select select = new Select().from(Inventory.class);
        List<Inventory> inventoryList = select.where(new Expression(select.getPool(), Conjunction.AND)
                .add(new Expression(select.getPool(),"FACILITY_ID", Operator.EQ,getProxy().getId()))
                .add(new Expression(select.getPool(),"SKU_ID",Operator.IN, AssetCode.getDeliverySkuIds().toArray(new Long[]{})))).execute();
        inventoryList = inventoryList.stream().filter(i-> (published == i.isPublished())).collect(Collectors.toList());

        if (inventoryList.isEmpty()){
            return null;
        }else if (inventoryList.size() > 1){
            throw new RuntimeException("Multiple Delivery Rules found!!");
        }else {
            return inventoryList.get(0);
        }
    }


    public double getDeliveryCharges(double distance) {
        Facility facility = getProxy();
        double charges = 0;
        if (facility.isDeliveryProvided()){
            charges =  facility.getMinDeliveryCharge();
            Inventory deliveryRule = getDeliveryRule(false);
            if (deliveryRule != null && ObjectUtil.isVoid(deliveryRule.getManagedBy())){
                double cf = UnitOfMeasureConversionTable.convert(1, UnitOfMeasure.MEASURES_PACKAGING,UnitOfMeasure.KILOMETERS, deliveryRule.getSku().getPackagingUOM().getName());
                cf = new DoubleHolder(cf,4).getHeldDouble().doubleValue();
                if (cf == 0){
                    throw new RuntimeException("Don't know how to convert " + deliveryRule.getSku().getPackagingUOM().getName() + " to " + UnitOfMeasure.KILOMETERS);
                }
                charges += ( deliveryRule.getSellingPrice() / cf ) * Math.round(Math.max(0,distance - facility.getMinChargeableDistance()));
            }
        }
        return charges;

    }

    public int getNumSkus(){
        if (getProxy().getRawRecord().isNewRecord() ){
            return 0;
        }
        ModelReflector<Inventory> ref = ModelReflector.instance(Inventory.class);
        List<Count> counts = new Select("count(1) AS COUNT","MAX(ID) AS ID").from(Inventory.class).where(
                new Expression(ref.getPool(),"FACILITY_ID",Operator.EQ,getProxy().getId())
        ).execute(Count.class);
        if (counts.isEmpty()){
            return 0;
        }
        return (int)(counts.get(0).getCount());
    }

    public void notifyEvent(String event, Order order) {
        Facility facility = getProxy();
        if (ObjectUtil.isVoid(facility.getNotificationUrl())){
            return;
        }
        JSONObject eventJSON = new JSONObject();
        eventJSON.put("Event",event);
        JSONObject orderJSON = new JSONObject();
        eventJSON.put("Order",orderJSON);

        ModelIOFactory.getWriter(Order.class,JSONObject.class).write(order,orderJSON,Order.getIncludedModelFields().get(Order.class),
                new HashSet<>(), Order.getIncludedModelFields());

        Call<?> call = new Call<>().url(facility.getNotificationUrl()).inputFormat(InputFormat.JSON).input(eventJSON).
                header("content-type","application/json").header("HumBhiOnline-Token",facility.getToken());
        TaskManager.instance().executeAsync(new ServerPushNotificationTask(call),true);
    }
    public void notifyEvent(String event, OrderLine line) {
        if (ObjectUtil.equals(line.getCancellationInitiator(),OrderLine.CANCELLATION_INITIATOR_COMPANY)){
            return;
        }
        Facility facility = getProxy();
        if (ObjectUtil.isVoid(facility.getNotificationUrl())){
            return;
        }
        JSONObject eventJSON = new JSONObject();
        eventJSON.put("Event",event);
        JSONObject orderLineJSON = new JSONObject();
        eventJSON.put("OrderLine",orderLineJSON);

        List<String> orderLineFields = Order.getIncludedModelFields().get(OrderLine.class);
        orderLineFields.add("SHIP_FROM_ID");

        ModelIOFactory.getWriter(OrderLine.class,JSONObject.class).write(line,orderLineJSON,orderLineFields,
                new HashSet<>(), Order.getIncludedModelFields());

        Call<?> call = new Call<>().url(facility.getNotificationUrl()).inputFormat(InputFormat.JSON).input(eventJSON).
                header("content-type","application/json").header("HumBhiOnline-Token",facility.getToken());
        TaskManager.instance().executeAsync(new ServerPushNotificationTask(call),true);
    }

    public static class ServerPushNotificationTask implements Task {
        Call<?> call = null;
        public ServerPushNotificationTask(Call<?> call){
            this.call = call;
        }
        public ServerPushNotificationTask(){

        }
        @Override
        public void execute() {
            if (this.call.method(HttpMethod.POST).hasErrors()){
                throw new RuntimeException(this.call.getError());
            }
        }
    }

    public List<User> getOperatingUsers(){
        List<Long> userIds = new SequenceSet<>();
        Facility facility = getProxy();
        userIds.add(facility.getCreatorUserId());

        for (UserFacility facilityUser : facility.getFacilityUsers()) {
            userIds.add(facilityUser.getUserId());
        }

        List<String> phoneNumbers = new SequenceSet<>();
        if (!ObjectUtil.isVoid(facility.getPhoneNumber())) {
            phoneNumbers.add(facility.getPhoneNumber());
        }
        if (!ObjectUtil.isVoid(facility.getAlternatePhoneNumber())){
            phoneNumbers.add(facility.getAlternatePhoneNumber());
        }
        if (!phoneNumbers.isEmpty()){
            Select select = new Select().from(UserPhone.class);
            List<UserPhone> userPhones = select.where(new Expression(select.getPool(), "PHONE_NUMBER",Operator.IN,phoneNumbers.toArray(new String[]{}))).execute();
            userPhones.stream().forEach(up->userIds.add(up.getUserId()));
        }
        List<User> users = new Select().from(User.class).where(new Expression(ModelReflector.instance(User.class).getPool(),"ID",
                Operator.IN , userIds.toArray(new Long[]{}))).execute();

        return users;
    }
}
