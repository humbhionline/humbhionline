package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import in.succinct.plugins.ecommerce.db.model.participation.PreferredCarrier;

public interface Order extends in.succinct.plugins.ecommerce.db.model.order.Order {

    @COLUMN_DEF(value = StandardDefault.SOME_VALUE, args = PreferredCarrier.HAND_DELIVERY)
    public String getPreferredCarrierName();

    @PARTICIPANT
    public Long getFacilityId();
    public void setFacilityId(Long facilityId);
    public Facility getFacility();

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isPaid();
    public void setPaid( boolean paid);

    @Override
    @PARTICIPANT
    Long getCreatorUserId();

    void completePayment();
}
