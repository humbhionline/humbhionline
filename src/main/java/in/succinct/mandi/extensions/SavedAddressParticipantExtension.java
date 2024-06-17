package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import in.succinct.mandi.db.model.SavedAddress;

import java.util.Arrays;
import java.util.List;

public class SavedAddressParticipantExtension extends ParticipantExtension<SavedAddress> {
    static {
        registerExtension(new SavedAddressParticipantExtension());
    }
    @Override
    public List<Long> getAllowedFieldValues(User user, SavedAddress partiallyFilledModel, String fieldName) {
        if (ObjectUtil.equals(fieldName,"USER_ID")){
            return Arrays.asList(user.getId());
        }
        return null;
    }
}
