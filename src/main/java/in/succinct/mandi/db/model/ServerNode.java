package in.succinct.mandi.db.model;

import com.venky.geo.GeoLocation;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.ENCRYPTED;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.model.DBPOOL;
import com.venky.swf.db.annotations.model.HAS_DESCRIPTION_FIELD;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.util.List;

@DBPOOL("telecom")
@HAS_DESCRIPTION_FIELD("BASE_URL")
public interface ServerNode extends Model, GeoLocation {
    @UNIQUE_KEY
    @COLUMN_DEF(StandardDefault.ONE)
    public Integer getNodeId();
    public void setNodeId(Integer nodeId);

    @UNIQUE_KEY("K2")
    public String getBaseUrl();
    public void setBaseUrl(String baseUrl);

    @IS_VIRTUAL
    public Long getPrimaryKeyOffset();


    public default ServerNode findNode(Long pkOfSomeTable){
        long nodeId = (pkOfSomeTable  >> 44);
        if (nodeId <= 0){
            return null;
        }

        List<ServerNode> nodes = new Select().from(ServerNode.class).
                where(new Expression(ModelReflector.instance(ServerNode.class).getPool(),
                        "NODE_ID", Operator.EQ, nodeId)).execute(1);
        if (nodes.isEmpty()){
            return null;
        }
        return nodes.get(0);
    }


    @IS_VIRTUAL
    boolean isSelf();
}
