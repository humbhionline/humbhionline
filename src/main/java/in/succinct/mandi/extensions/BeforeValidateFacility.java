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
        if (model.getRawRecord().isNewRecord() && verified) {
            throw new RuntimeException("First create the facility. Verification can be done as a next step.");
        }
        com.venky.swf.db.model.User user = model.getRawRecord().isNewRecord() ? Database.getInstance().getCurrentUser() : model.getCreatorUser();
        User creatorUser = user == null ? null : user.getRawRecord().getAsProxy(User.class);

        User currentUser = Database.getInstance().getCurrentUser().getRawRecord().getAsProxy(User.class);

        if ( addressChanged || !verified ){
            if (creatorUser != null){
                verified = true;
                for (String f : Address.getAddressFields()){
                    verified = verified &&
                            ObjectUtil.equals(creatorUser.getReflector().get(creatorUser,f), model.getReflector().get(model,f));
                    if (!verified){
                        break;
                    }
                }
                if (verified){
                    model.setVerifiedById(creatorUser.getId());
                    model.setLat(creatorUser.getLat());
                    model.setLng(creatorUser.getLng());
                }
            }
        }
        model.setPhoneNumber(Phone.sanitizePhoneNumber(model.getPhoneNumber()));
        model.setAlternatePhoneNumber(Phone.sanitizePhoneNumber(model.getAlternatePhoneNumber()));
        model.setVerified(verified);
        if (model.isVerified() && model.getRawRecord().isFieldDirty("VERIFIED")){
            if (model.getVerifiedById() != null){
                boolean verifiedOK ;
                if ( model.getVerifiedById() == creatorUser.getId() && creatorUser.isVerified()){
                    verifiedOK = true;
                }else if (currentUser.isStaff()) {
                    verifiedOK = true;
                }else {
                    verifiedOK = false;
                }
                if (!verifiedOK){
                    throw new RuntimeException("Insufficient Rights to verify Address.");
                }
            }
        }
    }
}
