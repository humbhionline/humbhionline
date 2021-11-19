package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.ui.HIDDEN;

@IS_VIRTUAL
public interface ShipToAddress extends OrderAddress {

    @HIDDEN
    public long getOrderId();

    @HIDDEN
    public Long getFacilityId();

    @HIDDEN
    public  String getAddressType();
}
