package in.succinct.mandi.db.model.beckn;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.model.Model;
import com.venky.swf.sql.Select;

import java.util.List;

public interface BecknNetwork  extends Model {
    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    public boolean isSubscriptionActive();
    public void setSubscriptionActive(boolean active);

    @UNIQUE_KEY
    public String getRegistryId();
    public void setRegistryId(String id);

    public String getRegistryUrl();
    public void setRegistryUrl(String registryUrl);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isProviderLocationRegistrySupported();
    public void setProviderLocationRegistrySupported(boolean providerLocationRegistrySupported);

    public String getRetailBppSubscriberId();
    public void setRetailBppSubscriberId(String subscriberId);

    @UNIQUE_KEY("BPP")
    public String getRetailBppUrl();
    public void setRetailBppUrl(String url);


    public String getDeliveryBapSubscriberId();
    public void setDeliveryBapSubscriberId(String subscriberId);

    @UNIQUE_KEY("BAP")
    public String getDeliveryBapUrl();
    public void setDeliveryBapUrl(String url);

    public  static BecknNetwork find(String registryId){
        BecknNetwork network = Database.getTable(BecknNetwork.class).newRecord();
        network.setRegistryId(registryId);
        return Database.getTable(BecknNetwork.class).find(network,false);
    }

    public static BecknNetwork findByRetailBppUrl(String bppUrl){
        BecknNetwork network = Database.getTable(BecknNetwork.class).newRecord();
        network.setRetailBppUrl(bppUrl);
        return Database.getTable(BecknNetwork.class).find(network,false);
    }
    public static BecknNetwork findByDeliveryBapUrl(String bapUrl){
        BecknNetwork network = Database.getTable(BecknNetwork.class).newRecord();
        network.setDeliveryBapUrl(bapUrl);
        return Database.getTable(BecknNetwork.class).find(network,false);
    }
    public static List<BecknNetwork> all(){
        return new Select().from(BecknNetwork.class).execute(BecknNetwork.class);
    }

}
