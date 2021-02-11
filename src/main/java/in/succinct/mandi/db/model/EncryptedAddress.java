package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.ENCRYPTED;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;

public interface EncryptedAddress extends Address {

    @ENCRYPTED
    public String getAddressLine1();

    @ENCRYPTED
    public String getAddressLine2();

    @ENCRYPTED
    public String getAddressLine3();

    @ENCRYPTED
    public String getAddressLine4();

    @ENCRYPTED
    public String getEmail();

    @ENCRYPTED
    public String getPhoneNumber();

    @ENCRYPTED
    public String getAlternatePhoneNumber();
}
