package in.succinct.mandi.db.model;

import com.venky.core.util.Bucket;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.model.Model;
import in.succinct.plugins.ecommerce.db.model.order.PersonAddress;

public interface SavedAddress extends Model, PersonAddress, EncryptedAddress {
    @UNIQUE_KEY
    @PARTICIPANT
    public Long getUserId();
    public void setUserId(Long id);
    public User getUser();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public String getLongName();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public String getAddressLine1();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public String getAddressLine2();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public String getAddressLine3();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public String getAddressLine4();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public Long getCityId();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public Long getStateId();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public Long getCountryId();

    @UNIQUE_KEY(allowMultipleRecordsWithNull = false)
    public Long getPinCodeId();

    public Bucket getNumDeliveries();
    public void setNumDeliveries(Bucket deliveries);

}
