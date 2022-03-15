package in.succinct.mandi.controller;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient.Mqtt3SubscribeAndCallbackBuilder.Call.Ex;
import com.venky.core.collections.SequenceSet;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.controller.ModelController;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.util.BoundingBox;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class LocalSearchController<M extends Model> extends ModelController<M> {
    public LocalSearchController(Path path) {
        super(path);
    }
    protected int getMaxDistance(){
        return Math.min(20,Math.max(5,getReflector().getJdbcTypeHelper().getTypeRef(Integer.class).getTypeConverter().
                valueOf(getPath().getFormFields().get("MaxDistance"))));
    }
    protected User getCurrentUser(){
        if (Database.getInstance().getCurrentUser() == null){
            return null;
        }
        User currentUser = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);
        if (currentUser.getCurrentLat() == null || currentUser.getCurrentLng() == null){
            currentUser.setCurrentLat(currentUser.getLat());
            currentUser.setCurrentLng(currentUser.getLng());
        }
        return currentUser;
    }
    private GeoCoordinate getCurrentUserLocation() {
        User currentUser = getCurrentUser();

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

    protected List<Long> getCurrentUserOperatedFacilityIds(){
        User user = getCurrentUser();
        if (user != null&& user.isSeller()) {
            return user.getOperatingFacilityIds();
        }
        return new SequenceSet<>();
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
    protected Expression getFacilityWhereClause(GeoCoordinate serviceRequirementLocation){
        return getFacilityWhereClause(serviceRequirementLocation,true);
    }
    protected Expression getFacilityWhereClause(GeoCoordinate serviceRequirementLocation,boolean onlyPublished){
        ModelReflector<Facility> ref = ModelReflector.instance(Facility.class);
        Expression fWhere = new Expression(ref.getPool(), Conjunction.AND);
        if (onlyPublished) {
            fWhere.add(new Expression(ref.getPool(), "PUBLISHED", Operator.EQ, true));
        }
        Expression or = new Expression(ref.getPool(), Conjunction.OR);
        fWhere.add(or);

        Expression deliveryProvidedWhere = new Expression(ref.getPool(),Conjunction.AND);
        deliveryProvidedWhere.add(new Expression(ref.getPool(),"DELIVERY_RADIUS",Operator.GT, 0));
        deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LAT",Operator.LE,serviceRequirementLocation.getLat()));
        deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LAT",Operator.GE,serviceRequirementLocation.getLat()));
        deliveryProvidedWhere.add(new Expression(ref.getPool(),"MIN_LNG",Operator.LE,serviceRequirementLocation.getLng()));
        deliveryProvidedWhere.add(new Expression(ref.getPool(),"MAX_LNG",Operator.GE,serviceRequirementLocation.getLng()));
        or.add(deliveryProvidedWhere);

        BoundingBox bb = new BoundingBox(new GeoCoordinate(serviceRequirementLocation),0,getMaxDistance());
        Expression deliveryNotProvidedWhere = bb.getWhereClause(Facility.class);
        or.add(deliveryNotProvidedWhere);


        return fWhere;
    }
    protected List<Long> getFacilityIds(GeoCoordinate serviceRequirementLocation){
        Expression fWhere = getFacilityWhereClause(serviceRequirementLocation);
        List<Facility> facilities = new Select().from(Facility.class).where(fWhere).execute();
        List<Long> facilityIds = DataSecurityFilter.getIds(facilities);
        User user = getCurrentUser();
        if (user != null && user.isSeller()) {
            facilityIds.addAll(user.getOperatingFacilityIds());
        }
        return facilityIds;
    }
    protected GeoCoordinate getServiceRequirementLocation(){
        Order order = getOrder();
        GeoCoordinate serviceRequirementLocation;
        if (order == null){
            serviceRequirementLocation = getCurrentUserLocation();
        }else {
            serviceRequirementLocation = new GeoCoordinate(order.getFacility());
        }
        return serviceRequirementLocation;
    }

    protected Expression getWhereClause(String facilityColumnName){
        Expression where = super.getWhereClause();
        GeoCoordinate serviceRequirementLocation = getServiceRequirementLocation();
        if (serviceRequirementLocation != null){
            List<Long> facilityIds = getFacilityIds(serviceRequirementLocation);
            if (!facilityIds.isEmpty()){
                where.add(Expression.createExpression(getReflector().getPool(),facilityColumnName, Operator.IN, facilityIds.toArray()));
            }else {
                where.add(new Expression(getReflector().getPool(), "ID",Operator.EQ, -1)); //Impossible!!
            }
        }
        return where;
    }





}
