package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;

import java.sql.Date;

public interface User extends com.venky.swf.plugins.mobilesignup.db.model.User {
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isVerified();
    public void setVerified(boolean kycComplete);

    public String getNameAsInBankAccount();
    public void setNameAsInBankAccount(String name);

    public  String getVirtualPaymentAddress();
    public void setVirtualPaymentAddress(String vpa);

    Date getDateOfBirth();
    void setDateOfBirth(Date dateOfBirth);



}
