package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import com.venky.swf.plugins.collab.extensions.participation.CompanySpecificParticipantExtension;
import com.venky.swf.pm.DataSecurityFilter;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;

import java.util.Arrays;
import java.util.List;

public class OrderParticipantExtension extends CompanySpecificParticipantExtension<Order> {
    static {
        registerExtension(new OrderParticipantExtension());
    }
    @Override
    public List<Long> getAllowedFieldValues(User user, Order partiallyFilledModel, String fieldName) {
        if (ObjectUtil.equals("FACILITY_ID",fieldName)){
            if (user.getRawRecord().getAsProxy(in.succinct.mandi.db.model.User.class).isStaff()){
                if (partiallyFilledModel.getFacilityId() > 0){
                    return Arrays.asList(partiallyFilledModel.getFacilityId());
                }else {
                    return null;
                }
            }else {
                return user.getRawRecord().getAsProxy(in.succinct.mandi.db.model.User.class).getOperatingFacilityIds();
            }
        }else if (ObjectUtil.equals("CREATOR_USER_ID",fieldName)){
            return Arrays.asList(user.getId());
        }
        return super.getAllowedFieldValues(user,partiallyFilledModel,fieldName);
    }
}
