package in.succinct.mandi.extensions;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.ParticipantExtension;

import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;


import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FacilityParticipantExtension extends ParticipantExtension<Facility> {
    static{
        registerExtension(new FacilityParticipantExtension());
    }
    @Override
    protected List<Long> getAllowedFieldValues(com.venky.swf.db.model.User fuser, Facility partiallyFilledModel, String fieldName) {
        User user = fuser.getRawRecord().getAsProxy(User.class);
        if (user.isStaff()){
            return null;
        }
        List<Long> ret ;
        if (ObjectUtil.equals(fieldName,"SELF_FACILITY_ID")) {
            ret = user.getOperatingFacilityIds();
            if (partiallyFilledModel.getId() > 0 ){
                ret.retainAll(Arrays.asList(partiallyFilledModel.getId()));
            }
        }else {
            ret = new SequenceSet<>();
        }
        return ret;
    }
}
