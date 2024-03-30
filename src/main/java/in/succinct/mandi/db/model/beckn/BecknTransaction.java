package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.model.User;

public interface BecknTransaction extends in.succinct.bap.shell.db.model.BecknTransaction {
    @PARTICIPANT
    @Index
    public Long getBuyerId();
    public void setBuyerId(Long id);
    public User getBuyer();



}
