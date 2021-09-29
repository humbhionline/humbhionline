package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.ENCRYPTED;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.model.DBPOOL;
import com.venky.swf.db.annotations.model.HAS_DESCRIPTION_FIELD;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.util.List;

@DBPOOL("registry")
@HAS_DESCRIPTION_FIELD("BASE_URL")
public interface ServerNode extends Model {
    @UNIQUE_KEY
    @COLUMN_DEF(StandardDefault.ONE)
    public long getNodeId();
    public void setNodeId(long nodeId);

    @UNIQUE_KEY("K3")
    public String getClientId();
    public void setClientId(String clientId);

    @UNIQUE_KEY("K2")
    public String getBaseUrl();
    public void setBaseUrl(String baseUrl);

    @ENCRYPTED
    public String getClientSecret();
    public void setClientSecret(String clientSecret);

    @COLUMN_SIZE(4096)
    public String getSigningPublicKey();
    public void setSigningPublicKey(String  publicKey);

    @COLUMN_SIZE(4096)
    public String getEncryptionPublicKey();
    public void setEncryptionPublicKey(String encryptionPublicKey);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isApproved();
    public void setApproved(boolean approved);

    @IS_VIRTUAL
    public Long getPrimaryKeyOffset();


    public static ServerNode findNode(String clientId){
        List<ServerNode> nodes = new Select().from(ServerNode.class).
                where(new Expression(ModelReflector.instance(ServerNode.class).getPool(),
                        "CLIENT_ID", Operator.EQ, clientId)).execute(1);
        if (nodes.isEmpty()){
            return null;
        }
        ServerNode node = nodes.get(0);
        return node.isApproved()? node : null;
    }
    public static ServerNode findNodeByBaseUrl(String baseUrl){
        return findNodeByBaseUrl(baseUrl,true);
    }
    public static ServerNode findNodeByBaseUrl(String baseUrl, boolean ensureApproved){
        List<ServerNode> nodes = new Select().from(ServerNode.class).
                where(new Expression(ModelReflector.instance(ServerNode.class).getPool(),
                        "BASE_URL", Operator.EQ, baseUrl)).execute(1);
        if (nodes.isEmpty()){
            return null;
        }
        ServerNode node = nodes.get(0);
        return (!ensureApproved || node.isApproved()) ? node : null;
    }
    public static ServerNode selfNode(){
        return findNodeByBaseUrl(Config.instance().getServerBaseUrl(),false);
    }

    public static ServerNode findNodeByNodeId(long nodeId){
        List<ServerNode> nodes = new Select().from(ServerNode.class).
                where(new Expression(ModelReflector.instance(ServerNode.class).getPool(),
                        "NODE_ID", Operator.EQ, nodeId)).execute(1);
        if (nodes.isEmpty()){
            return null;
        }
        ServerNode node = nodes.get(0);
        return node.isApproved()? node : null;

    }
    public static ServerNode findNode(long pkOfSomeTable){
        long nodeId = (pkOfSomeTable  >> 44);
        if (nodeId <= 0){
            return null;
        }
        return findNodeByNodeId(nodeId);
    }


    @IS_VIRTUAL
    public boolean isRegistry();

    @IS_VIRTUAL
    boolean isSelf();

    @IS_VIRTUAL
    public String getAuthorizationHeader();

    public List<TelecomCircle> getTelecomCircles();
}
