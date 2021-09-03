package in.succinct.mandi.db.model;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.integration.api.Call;
import com.venky.swf.plugins.collab.db.model.user.UserFacility;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.pm.DataSecurityFilter;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
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

    public List<Long> getOperatingFacilityIds() {
        User user = getProxy();
        SequenceSet<Long> ret = new SequenceSet<>();
        for (UserFacility uf : user.getUserFacilities()) {
            ret.add(uf.getFacilityId());
        }

        Set<String> phoneNumbers= new HashSet<>();
        for (UserPhone userPhone : user.getUserPhones()) {
            if (userPhone.isValidated()) {
                phoneNumbers.add(userPhone.getPhoneNumber());
            }
        }
        Expression where = new Expression(getReflector().getPool(), Conjunction.OR);
        if (!phoneNumbers.isEmpty()){
            where.add(new Expression(getReflector().getPool(),"PHONE_NUMBER", Operator.IN, phoneNumbers.toArray()));
            where.add(new Expression(getReflector().getPool(),"ALTERNATE_PHONE_NUMBER", Operator.IN, phoneNumbers.toArray()));
        }
        where.add(new Expression(getReflector().getPool(), "CREATOR_ID",Operator.EQ, user.getId()) );
        List<Facility> facilities = new Select().from(Facility.class).where(where).execute();
        ret.addAll(DataSecurityFilter.getIds(facilities));
        return ret;
    }
}
