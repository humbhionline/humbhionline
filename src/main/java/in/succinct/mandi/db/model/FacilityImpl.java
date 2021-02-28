package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.User;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.integrations.courier.Wefast;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
            User currentUser = Database.getInstance().getCurrentUser();
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

    public Inventory getDeliveryRule(){
        Select select = new Select().from(Inventory.class);
        List<Inventory> inventoryList = select.where(new Expression(select.getPool(), Conjunction.AND)
                .add(new Expression(select.getPool(),"FACILITY_ID", Operator.EQ,getProxy().getId()))
                .add(new Expression(select.getPool(),"SKU_ID",Operator.IN, AssetCode.getDeliverySkuIds().toArray(new Long[]{})))).execute();
        inventoryList = inventoryList.stream().filter(i-> !i.isPublished()).collect(Collectors.toList());

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
        Double charges = null;
        if (facility.isDeliveryProvided()){
            Inventory deliveryRule = getDeliveryRule();
            charges =  facility.getFixedDeliveryCharges();
            if (deliveryRule != null && ObjectUtil.isVoid(deliveryRule.getManagedBy())){
                charges = facility.getFixedDeliveryCharges();
                double cf = UnitOfMeasureConversionTable.convert(1, UnitOfMeasure.MEASURES_PACKAGING,UnitOfMeasure.KILOMETERS, deliveryRule.getSku().getPackagingUOM().getName());
                charges += deliveryRule.getSellingPrice() * Math.round( (Math.max(0,distance - facility.getMinFixedDistance()))/Math.max(cf,1));
            }
        }
        return charges;

    }

}
