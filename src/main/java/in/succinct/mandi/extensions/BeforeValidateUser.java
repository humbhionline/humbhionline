package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.plugins.ecommerce.db.model.participation.User;

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

    }

    private void validateAddress(User u){
        in.succinct.mandi.db.model.User model = u.getRawRecord().getAsProxy(in.succinct.mandi.db.model.User.class);
        boolean addressChanged = Address.isAddressChanged(model);

        boolean verifiedViaKyc = !model.getReflector().getJdbcTypeHelper().getTypeRef(Boolean.class).
                getTypeConverter().valueOf(model.getTxnProperty("verifiedViaKyc"));
        User currentUser = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);

        if (model.isVerified() && !model.getRawRecord().isFieldDirty("VERIFIED")){
            if (addressChanged && !verifiedViaKyc && !currentUser.isStaff()){
                model.setVerified(false);
            }
        }

        if (model.getRawRecord().isNewRecord() && model.isVerified()) {
            throw new RuntimeException("First create the user. Verification can be done as a next step.");
        }

        if (model.getRawRecord().isFieldDirty("VERIFIED")){
            if (model.isVerified()) {
                if (!verifiedViaKyc && !currentUser.isStaff()){
                    throw new RuntimeException("Insufficient Rights to verify Address.");
                }
            }
        }
        if (addressChanged){
            model.setLat(model.getCurrentLat());
            model.setLng(model.getCurrentLng());
        }

    }
}
