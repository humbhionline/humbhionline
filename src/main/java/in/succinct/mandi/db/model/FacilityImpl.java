package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoordinate;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.User;
import com.venky.swf.db.table.ModelImpl;
import in.succinct.plugins.ecommerce.db.model.catalog.Item;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasureConversionTable;
import in.succinct.plugins.ecommerce.db.model.inventory.Inventory;

import java.math.BigDecimal;
import java.util.Optional;

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

    public double getDeliveryCharges(){
        Facility facility = getProxy();
        Optional<Inventory> inventoryOptional = facility.getInventoryList().stream().filter(i->{
            Sku sku = i.getSku().getRawRecord().getAsProxy(Sku.class);
            Item item = sku.getItem();
            if (item.getAssetCodeId() != null){
                return (ObjectUtil.equals(item.getAssetCode().getCode(),"996813")) && !sku.isPublished(); //If pulished. It is courier and added as item in his order line.
            }else {
                return false;
            }
        }).findFirst();
        double charges = facility.getFixedDeliveryCharges();
        if (inventoryOptional.isPresent()){
            Inventory inventory = inventoryOptional.get();
            double cf = UnitOfMeasureConversionTable.convert(1, UnitOfMeasure.MEASURES_PACKAGING,UnitOfMeasure.KILOMETERS, inventory.getSku().getPackagingUOM().getName());

            charges += inventory.getSellingPrice() * Math.round(getDistance()/Math.max(cf,1));
        }
        return charges;
    }

}
