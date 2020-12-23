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
        Set<String> phoneNumbers= new HashSet<>();
        for (UserPhone userPhone : user.getUserPhones()) {
            if (userPhone.isValidated()) {
                phoneNumbers.add(userPhone.getPhoneNumber());
            }
        }
        SequenceSet<Long> ret = null;

        if (ObjectUtil.equals(fieldName,"SELF_FACILITY_ID")) {
            ret = new SequenceSet<>();
            for (UserFacility uf : user.getUserFacilities()) {
                if (partiallyFilledModel.getReflector().isVoid(partiallyFilledModel.getId())) {
                    ret.add(uf.getFacilityId());
                } else if (partiallyFilledModel.getId() == uf.getFacilityId()) {
                    ret.add(uf.getFacilityId());
                    break;
                }
            }
            if (partiallyFilledModel.getId() > 0 && !ObjectUtil.isVoid(partiallyFilledModel.getPhoneNumber()) && phoneNumbers.contains(partiallyFilledModel.getPhoneNumber())){
                ret.add(partiallyFilledModel.getId());
            }else {
                Expression where = new Expression(getReflector().getPool(), Conjunction.OR);
                if (!phoneNumbers.isEmpty()){
                    where.add(new Expression(getReflector().getPool(),"PHONE_NUMBER", Operator.IN, phoneNumbers.toArray()));
                }
                where.add(new Expression(getReflector().getPool(), "CREATOR_ID",Operator.EQ, user.getId()) );
                List<Facility> facilities = new Select().from(Facility.class).where(where).execute();
                ret.addAll(DataSecurityFilter.getIds(facilities));
            }
        }
        return ret;
    }
}
