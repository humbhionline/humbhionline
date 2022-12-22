package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.CompanyUtil;


import java.util.Optional;

public class BeforeValidateUser extends BeforeModelValidateExtension<User> {
    static {
        registerExtension(new BeforeValidateUser());
    }
    @Override
    public void beforeValidate(User model) {

        if (!ObjectUtil.isVoid(model.getPhoneNumber())){
            model.setPhoneNumber(Phone.sanitizePhoneNumber(model.getPhoneNumber()));
        }
        if (ObjectUtil.isVoid(model.getCompanyId())){
            model.setCompanyId(CompanyUtil.getCompanyId());
        }
        if (!ObjectUtil.isVoid(model.getAlternatePhoneNumber())){
            model.setAlternatePhoneNumber(Phone.sanitizePhoneNumber(model.getAlternatePhoneNumber()));
        }

        if (!model.getRawRecord().isNewRecord() && model.getRawRecord().isFieldDirty("PHONE_NUMBER") &&
                !ObjectUtil.isVoid(model.getPhoneNumber())){
            Optional<UserPhone> optionalUserPhone = model.getUserPhones().stream().filter(up->ObjectUtil.equals(up.getPhoneNumber(),model.getPhoneNumber())).findFirst();
            if (!optionalUserPhone.isPresent()){
                throw new RuntimeException ( "Phone needs to verified before you can make it primary.");
            }else {
                UserPhone userPhone = optionalUserPhone.get();
                if (!userPhone.isValidated()){
                    throw new RuntimeException ( "Phone needs to verified before you can make it primary.");
                }
            }
        }
        validateAddress(model);
        if (model.getRawRecord().isNewRecord() && CompanyUtil.isHumBhiOnlineSubscriptionItemPresent()){
            model.setBalanceOrderLineCount(50);//Signup bonus.!
        }
    }

    private void validateAddress(User u){
        in.succinct.mandi.db.model.User model = u.getRawRecord().getAsProxy(in.succinct.mandi.db.model.User.class);
        boolean addressChanged = Address.isAddressChanged(model);

        boolean verifiedViaKyc = model.getReflector().getJdbcTypeHelper().getTypeRef(Boolean.class).
                getTypeConverter().valueOf(model.getTxnProperty("verifiedViaKyc"));
        com.venky.swf.db.model.User cu = Database.getInstance().getCurrentUser();
        User currentUser = cu == null ? null : cu.getRawRecord().getAsProxy(User.class);

        if (model.isVerified() && !model.getRawRecord().isFieldDirty("VERIFIED")){
            model.setVerified(!addressChanged);
        }

        if (model.getRawRecord().isNewRecord() && model.isVerified()) {
            throw new RuntimeException("First create the user. Verification can be done as a next step.");
        }

        if (model.getRawRecord().isFieldDirty("VERIFIED")){
            if (model.isVerified()) {
                if (!verifiedViaKyc && currentUser != null && !currentUser.isStaff()){
                    throw new RuntimeException("Insufficient Rights to verify Address.");
                }
            }
        }
        if (addressChanged){
            model.setLat(currentUser.getCurrentLat());
            model.setLng(currentUser.getCurrentLng());
        }

    }
}
