package in.succinct.mandi.db.model;

import com.venky.swf.db.table.ModelImpl;

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

}
