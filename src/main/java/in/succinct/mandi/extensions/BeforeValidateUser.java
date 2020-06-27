package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.routing.Config;
import in.succinct.plugins.ecommerce.db.model.participation.User;

import java.util.Optional;

public class BeforeValidateUser extends BeforeModelValidateExtension<User> {
    static {
        registerExtension(new BeforeValidateUser());
    }
    @Override
    public void beforeValidate(User model) {
        if (model.getId() > 1){
            //Other than root all users name must be phonenumber.
            model.setName(Phone.sanitizePhoneNumber(model.getName()).substring(3));
        }

        if (!ObjectUtil.isVoid(model.getPhoneNumber())){
            model.setPhoneNumber(Phone.sanitizePhoneNumber(model.getPhoneNumber()));
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
