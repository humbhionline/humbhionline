package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.HOUSEKEEPING;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.db.annotations.column.ui.PROTECTION;
import com.venky.swf.db.annotations.column.validations.Enumeration;

import java.util.List;

public interface Facility extends in.succinct.plugins.ecommerce.db.model.participation.Facility {
    @COLUMN_NAME("ID")
    @PROTECTION
    @HIDDEN
    @HOUSEKEEPING
    @PARTICIPANT
    public long getSelfFacilityId();
    public void setSelfFacilityId(long id);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isVerified();
    public void setVerified(boolean verified);

    @IS_NULLABLE
    public Long getVerifiedById();
    public void setVerifiedById(Long id);
    public User getVerifiedBy();


    public void verify();

    @IS_VIRTUAL
    public Facility getSelfFacility();


    @IS_VIRTUAL
    public Double getDistance();
    public void setDistance(Double distance);

    public String getGSTIN();
    public void setGSTIN(String gstin);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isDeliveryProvided();
    public void setDeliveryProvided(double deliveryProvided);


    public List<DeliveryRule> getDeliveryRules();

    public void shutdown();

}
