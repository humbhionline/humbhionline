package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.PhoneImpl;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;


public class BeforeValidateFacility extends BeforeModelValidateExtension<Facility> {
    static {
        registerExtension(new BeforeValidateFacility());
    }

    @Override
    public void beforeValidate(Facility model) {
        if (model.getCityId() != null) {
            model.setStateId(model.getCity().getStateId());
        }
        if (model.getStateId() != null) {
            model.setCountryId(model.getState().getCountryId());
        }

        boolean addressChanged = Address.isAddressChanged(model);
        boolean verified = model.isVerified();
        if ( addressChanged || !verified ){
            com.venky.swf.db.model.User user = Database.getInstance().getCurrentUser();
            if (user != null){
                User u = user.getRawRecord().getAsProxy(User.class);
                verified = true;
                for (String f : Address.getAddressFields()){
                    verified = verified &&
                            ObjectUtil.equals(u.getReflector().get(u,f), model.getReflector().get(model,f));
                    if (!verified){
                        break;
                    }
                }
            }
        }
        model.setPhoneNumber(Phone.sanitizePhoneNumber(model.getPhoneNumber()));
        model.setAlternatePhoneNumber(Phone.sanitizePhoneNumber(model.getAlternatePhoneNumber()));
        model.setVerified(verified);
    }
}
