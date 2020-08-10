package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.routing.Config;
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

    }
}
