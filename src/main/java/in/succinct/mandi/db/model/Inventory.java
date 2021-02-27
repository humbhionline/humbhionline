package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.validations.Enumeration;

public interface Inventory extends in.succinct.plugins.ecommerce.db.model.inventory.Inventory {

    //These Virtual fields are for inventory search!!
    @IS_VIRTUAL
    public Double getDeliveryCharges();
    public void setDeliveryCharges(Double  deliveryCharges);

    @IS_VIRTUAL
    public boolean isDeliveryProvided();
    public void setDeliveryProvided(boolean deliveryProvided);

    @IS_VIRTUAL
    public Double getChargeableDistance();
    public void setChargeableDistance(Double distance);

    @Index
    public String getTags();
    public void setTags(String tags);

    //Valid for delivery items!!
    public static final String WEFAST  = "wefast" ;
    @Enumeration(" ,"+WEFAST)
    @IS_NULLABLE
    public String getManagedBy();
    public void setManagedBy(String managedBy);
}
