package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.model.Model;


public interface OrderCancellationReason extends Model {
    @COLUMN_DEF(StandardDefault.ZERO)
    public int getSequence();
    public void setSequence(int sequence);

    public boolean isUsableBeforeDelivery();
    public void setUsableBeforeDelivery(boolean usableBeforeDelivery);

    public boolean isUsableAfterDelivery();
    public void setUsableAfterDelivery(boolean usableAfterDelivery);

    @UNIQUE_KEY
    public String getReason();
    public void setReason(String reason);

}
