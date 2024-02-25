package in.succinct.mandi.db.model.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.model.HAS_DESCRIPTION_FIELD;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.onet.core.adaptor.NetworkAdaptor;

import java.util.List;

@HAS_DESCRIPTION_FIELD("NETWORK_ID")
public interface BecknNetwork  extends Model {

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isDisabled();
    public void setDisabled(boolean disabled);

    @UNIQUE_KEY
    @COLUMN_NAME("REGISTRY_ID")
    public String getNetworkId();
    public void setNetworkId(String id);

    @IS_VIRTUAL
    public String getRegistryUrl();

    @IS_NULLABLE
    public String getSubscriberId();
    public void setSubscriberId(String subscriberId);


    public String getCryptoKeyId();
    public void setCryptoKeyId(String cryptoKeyId);


    public Integer getPriority();
    public void setPriority(Integer priority);

    public  static BecknNetwork find(String networkId){
        BecknNetwork network = Database.getTable(BecknNetwork.class).newRecord();
        network.setNetworkId(networkId);
        return Database.getTable(BecknNetwork.class).find(network,false);
    }

    public static BecknNetwork findByUrl(String bppUrl){
        String[] parts = bppUrl.split("/");
        String networkId = null ;
        for (String part : parts) {
            if (!ObjectUtil.isVoid(part)) {
                networkId = part;
                break;
            }
        }
        return find(networkId);
    }
    public static List<BecknNetwork> all(){
        return new Select().from(BecknNetwork.class).where(
                new Expression(ModelReflector.instance(BecknNetwork.class).getPool(), Conjunction.AND).
                        add(new Expression(ModelReflector.instance(BecknNetwork.class).getPool(),"DISABLED", Operator.EQ,false))
        ).orderBy("PRIORITY").execute(BecknNetwork.class);
    }

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isMqSupported();
    public void setMqSupported(boolean mqSupported);

    public String getMqHost();
    public void setMqHost(String url);

    public Integer getMqPort();
    public void setMqPort(Integer port);

    public String getMqProvider();
    public void setMqProvider(String mqProvider);


    public List<BecknNetworkRole> getBecknNetworkRoles();

    @IS_VIRTUAL
    public NetworkAdaptor getNetworkAdaptor();





    @IS_VIRTUAL
    Subscriber getBppSubscriber();

    @IS_VIRTUAL
    Subscriber getBapSubscriber();


    public void subscribe();
}
