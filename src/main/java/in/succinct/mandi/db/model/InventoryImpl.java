package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.table.ModelImpl;

import java.util.HashSet;
import java.util.Set;

public class InventoryImpl extends ModelImpl<Inventory> {
    public InventoryImpl(){

    }
    public InventoryImpl(Inventory inventory){
        super(inventory);
    }
    Double deliveryCharges;
    public Double getDeliveryCharges() {
        return deliveryCharges;
    }
    public void setDeliveryCharges(Double deliveryCharges){
        this.deliveryCharges = deliveryCharges;
    }

    boolean deliveryProvided = false;
    public boolean isDeliveryProvided() {
        return deliveryProvided;
    }
    public void setDeliveryProvided(boolean deliveryProvided){
        this.deliveryProvided = deliveryProvided;
    }

    Double chargeableDistance;
    public Double getChargeableDistance() {
        return chargeableDistance;
    }
    public void setChargeableDistance(Double distance){
        this.chargeableDistance = distance;
    }


    Boolean external = false;
    public Boolean isExternal() {
        return external;
    }
    public void setExternal(Boolean external) {
        this.external =external;
    }

    String externalSkuId = null;
    public String getExternalSkuId(){
        return externalSkuId;
    }
    public void setExternalSkuId(String externalSkuId){
        this.externalSkuId = externalSkuId;
    }

    String externalFacilityId = null;
    public String getExternalFacilityId(){
        return externalFacilityId;
    }
    public void setExternalFacilityId(String facilityId){
        this.externalFacilityId = facilityId;
    }

    String networkId = null;
    public String getNetworkId() {
        return networkId;
    }
    public void setNetworkId(String networkId){
        this.networkId = networkId;
    }

}
