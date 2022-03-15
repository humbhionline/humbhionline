package in.succinct.mandi.db.model;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.integration.api.Call;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.util.CompanyUtil;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserImpl  extends ModelImpl<User> {
    public UserImpl(){
        super();
    }
    public UserImpl(User user){
        super(user);
    }

    public boolean isPasswordSet() {
        return !ObjectUtil.isVoid(getProxy().getPassword());
    }

    public void setPasswordSet(boolean set){
        //Do nothing.
    }

    List<Long> operatingFacilityIds = null;
    public List<Long> getOperatingFacilityIds() {
        if (operatingFacilityIds == null) {
            operatingFacilityIds = new SequenceSet<>();
            User user = getProxy();
            for (UserFacility uf : user.getUserFacilities()) {
                operatingFacilityIds.add(uf.getFacilityId());
            }

            Set<String> phoneNumbers = new HashSet<>();
            for (UserPhone userPhone : user.getUserPhones()) {
                if (userPhone.isValidated()) {
                    phoneNumbers.add(userPhone.getPhoneNumber());
                }
            }
            Expression where = new Expression(getReflector().getPool(), Conjunction.OR);
            if (!phoneNumbers.isEmpty()) {
                where.add(new Expression(getReflector().getPool(), "PHONE_NUMBER", Operator.IN, phoneNumbers.toArray()));
                where.add(new Expression(getReflector().getPool(), "ALTERNATE_PHONE_NUMBER", Operator.IN, phoneNumbers.toArray()));
            }
            where.add(new Expression(getReflector().getPool(), "CREATOR_ID", Operator.EQ, user.getId()));
            List<Facility> facilities = new Select().from(Facility.class).where(where).execute();
            operatingFacilityIds.addAll(DataSecurityFilter.getIds(facilities));
        }
        return operatingFacilityIds;
    }

    @IS_VIRTUAL
    public Boolean isLoggedInToCustomDomain(){
        return CompanyUtil.getFacilityForCustomDomain() != null;
    }
}
