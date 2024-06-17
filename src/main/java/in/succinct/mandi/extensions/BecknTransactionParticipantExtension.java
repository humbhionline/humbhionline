package in.succinct.mandi.extensions;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import in.succinct.bap.shell.db.model.BecknTransaction;

import java.util.Collections;
import java.util.List;

public class BecknTransactionParticipantExtension extends ParticipantExtension<BecknTransaction> {
    static {
        registerExtension(new BecknTransactionParticipantExtension());
    }
    @Override
    public List<Long> getAllowedFieldValues(User user, BecknTransaction partiallyFilledModel, String fieldName) {
        if (fieldName.equals("BUYER_ID")){
            in.succinct.mandi.db.model.User u  = user.getRawRecord().getAsProxy(in.succinct.mandi.db.model.User.class);
            if (u.isStaff()){
                return null;
            }else {
                return Collections.singletonList(user.getId());
            }
        }
        return new SequenceSet<>();
    }
}
