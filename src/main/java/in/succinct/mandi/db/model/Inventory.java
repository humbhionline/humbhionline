package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;

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


    @IS_VIRTUAL
    public String getNetworkId();
    public void setNetworkId(String networkId);


    @IS_VIRTUAL
    public Boolean isExternal();
    public void setExternal(Boolean external);

    @IS_VIRTUAL
    public String getExternalSkuId();
    public void setExternalSkuId(String externalSkuId);


    @IS_VIRTUAL
    public String getExternalFacilityId();
    public void setExternalFacilityId(String facilityId);

}
