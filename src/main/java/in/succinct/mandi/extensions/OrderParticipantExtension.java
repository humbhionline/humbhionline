package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import com.venky.swf.pm.DataSecurityFilter;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;

import java.util.Arrays;
import java.util.List;

public class OrderParticipantExtension extends ParticipantExtension<Order> {
    static {
        registerExtension(new OrderParticipantExtension());
    }
    @Override
    protected List<Long> getAllowedFieldValues(User user, Order partiallyFilledModel, String fieldName) {
        if (ObjectUtil.equals("FACILITY_ID",fieldName)){
            return DataSecurityFilter.getIds(DataSecurityFilter.getRecordsAccessible(Facility.class,user));
        }else if (ObjectUtil.equals("CREATOR_USER_ID",fieldName)){
            return Arrays.asList(user.getId());
        }
        return null;
    }
}
