package in.succinct.mandi.db.model;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.model.DBPOOL;
import com.venky.swf.db.model.Model;
import com.venky.swf.plugins.collab.db.model.user.Phone;

@DBPOOL("telecom")
public interface MobileMeta extends Model {

    @Index
    @UNIQUE_KEY
    public String getPrefix();
    public void setPrefix(String prefix);

    @IS_NULLABLE
    @Index
    public Long getTelecomOperatorId();
    public void setTelecomOperatorId(Long id);
    public TelecomOperator getTelecomOperator();

    @IS_NULLABLE
    @Index
    public Long getTelecomCircleId();
    public void setTelecomCircleId(Long id);
    public TelecomCircle getTelecomCircle();

    @IS_VIRTUAL
    public Long getServerNodeId();
    public void setServerNodeId(Long id);
    public ServerNode getServerNode();


    public static MobileMeta find(String phoneNumber){
        if (ObjectUtil.isVoid(phoneNumber)){
            return null;
        }
        String sanitized = Phone.sanitizePhoneNumber(phoneNumber);
        String prefix = sanitized.substring(3,3+4);
        MobileMeta meta = Database.getTable(MobileMeta.class).newRecord();
        meta.setPrefix(prefix);
        meta = Database.getTable(MobileMeta.class).getRefreshed(meta);
        if (meta.getRawRecord().isNewRecord()){
            return null;
        }else {
            return meta;
        }
    }
}
