package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.user.Phone;
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

        validateAddress(model);
    }

    private void validateAddress(Facility model){
        boolean addressChanged = Address.isAddressChanged(model);
        com.venky.swf.db.model.User user = model.getRawRecord().isNewRecord() ? Database.getInstance().getCurrentUser() : model.getCreatorUser();
        User creatorUser = user == null ? null : user.getRawRecord().getAsProxy(User.class);

        if ( addressChanged ){
            if (creatorUser != null){
                boolean isUserResidence = true;
                for (String f : Address.getAddressFields()){
                    isUserResidence = isUserResidence &&
                            ObjectUtil.equals(creatorUser.getReflector().get(creatorUser,f), model.getReflector().get(model,f));
                    if (!isUserResidence){
                        break;
                    }
                }
                if (isUserResidence){
                    model.setLat(creatorUser.getLat());
                    model.setLng(creatorUser.getLng());
                }
            }
        }
        model.setPhoneNumber(Phone.sanitizePhoneNumber(model.getPhoneNumber()));
        model.setAlternatePhoneNumber(Phone.sanitizePhoneNumber(model.getAlternatePhoneNumber()));
    }
}
