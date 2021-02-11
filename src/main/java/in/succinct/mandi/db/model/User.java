package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;

import java.sql.Date;
import java.util.List;

public interface User extends EncryptedAddress, com.venky.swf.plugins.mobilesignup.db.model.User {
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    @Index
    public boolean isVerified();
    public void setVerified(boolean kycComplete);

    public String getNameAsInBankAccount();
    public void setNameAsInBankAccount(String name);

    public  String getVirtualPaymentAddress();
    public void setVirtualPaymentAddress(String vpa);

    Date getDateOfBirth();
    void setDateOfBirth(Date dateOfBirth);


    @COLUMN_DEF(StandardDefault.ZERO)
    public double getBalanceOrderLineCount();
    public void setBalanceOrderLineCount(double balance);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isBalanceBelowThresholdAlertSent();
    public void setBalanceBelowThresholdAlertSent(boolean balanceBelowThresholdAlertSent);

    @IS_VIRTUAL
    public boolean isPasswordSet();
    public void setPasswordSet(boolean passwordSet);

    @COLUMN_DEF(StandardDefault.NULL)
    @Index
    public Boolean isSeller();
    public void setSeller(Boolean seller);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    @Index
    public boolean isTermsAccepted();
    public void setTermsAccepted(boolean termsAccepted);

    @IS_VIRTUAL
    public List<Long> getOperatingFacilityIds();

    public List<UserLocation> getUserLocations();
}
