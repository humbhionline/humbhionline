package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.util.List;

public interface Item  extends in.succinct.plugins.ecommerce.db.model.catalog.Item {
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isItemRestrictedToSingleSeller();
    void setItemRestrictedToSingleSeller(boolean itemRestrictedToSingleSeller);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isHumBhiOnlineSubscriptionItem();
    void setHumBhiOnlineSubscriptionItem(boolean humBhiOnlineSubscriptionItem);

    public String getTags();
    public void setTags(String tags);
}
