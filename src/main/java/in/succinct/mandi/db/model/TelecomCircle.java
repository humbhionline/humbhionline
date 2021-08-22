package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.model.DBPOOL;
import com.venky.swf.db.model.Model;
@DBPOOL("telecom")
public interface TelecomCircle extends Model {
    @UNIQUE_KEY
    @Index
    public String getName();
    public void setName(String name);

    @UNIQUE_KEY("K2")
    @Index
    public String getCode();
    public void setCode(String code);

    public Long getServerNodeId();
    public void setServerNodeId(Long id);
    public ServerNode getServerNode();

}
