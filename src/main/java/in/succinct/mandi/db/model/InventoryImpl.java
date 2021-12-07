package in.succinct.mandi.db.model;

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


    private final Set<String> courierIntegrators = new HashSet<String>(){{
       add(Inventory.BECKN);
    }};
    public boolean isCourierAggregator(){
        Inventory inventory = getProxy();
        return courierIntegrators.contains(inventory.getManagedBy());
    }

    String ref = null;
    public String getQuoteRef(){
        return ref;
    }
    public void setQuoteRef(String ref){
        this.ref = ref;
    }
}
