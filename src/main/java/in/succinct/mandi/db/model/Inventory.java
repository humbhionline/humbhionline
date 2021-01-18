package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;

public interface Inventory extends in.succinct.plugins.ecommerce.db.model.inventory.Inventory {
    @IS_VIRTUAL
    public Double getDeliveryCharges();
    public void setDeliveryCharges(Double  deliveryCharges);

    @IS_VIRTUAL
    public boolean isDeliveryProvided();
    public void setDeliveryProvided(boolean deliveryProvided);

    @IS_VIRTUAL
    public Double getChargeableDistance();
    public void setChargeableDistance(Double distance);


}
