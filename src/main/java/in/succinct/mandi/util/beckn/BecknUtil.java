package in.succinct.mandi.util.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.plugins.collab.db.model.CryptoKey;
import com.venky.swf.routing.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknUtil {
    public static String getSubscriberId(){
        return Config.instance().getHostName();
    }
    public static String getIdPrefix(){
        //return "./nic2004:52110/IND.std:080/";
        return "./retail.kirana/ind.blr/";
    }
    public static String getIdSuffix(){
        return getSubscriberId();
    }
    public enum Entity {
        fulfillment,
        category,
        provider,
        provider_category,
        provider_location,
        item,
        catalog,
        cancellation_reason,
        return_reason,
        order
    }
    public static String getBecknId(Long localUniqueId,Entity becknEntity){
        return getBecknId(String.valueOf(localUniqueId),becknEntity);
    }
    public static String getBecknId(String localUniqueId,Entity becknEntity){
        StringBuilder builder = new StringBuilder();
        builder.append(getIdPrefix());
        if (!ObjectUtil.isVoid(localUniqueId)){
            builder.append(localUniqueId).append("@");
        }
        builder.append(getIdSuffix());
        if (becknEntity != null){
            builder.append(".").append(becknEntity);
        }
        return builder.toString();
    }
    public static String getLocalUniqueId(String beckId, Entity becknEntity){
        String  pattern = String.format("^%s(.*)@%s[.]%s$",getIdPrefix(),getIdSuffix(),becknEntity == null ? "" : becknEntity.toString());
        Matcher matcher = Pattern.compile(pattern).matcher(beckId);
        List<String> ids = new ArrayList<>();
        while(matcher.find()){
            ids.add(matcher.group(1));
        }
        if (ids.size() == 1){
            return ids.get(0);
        }
        return "-1" ;//throw new RuntimeException("Id not formated as expected!");
    }

    public static String getRegistryUrl(){
        return Config.instance().getProperty("beckn.registry.url");

    }
    public static CryptoKey getSelfEncryptionKey(){
        CryptoKey encryptionKey = Database.getTable(CryptoKey.class).newRecord();
        encryptionKey.setAlias(Config.instance().getHostName() + ".encrypt.k1");
        encryptionKey = Database.getTable(CryptoKey.class).getRefreshed(encryptionKey);
        if (encryptionKey.getRawRecord().isNewRecord()){
            return null;
        }
        return encryptionKey;
    }
    public static CryptoKey getSelfKey(){
        CryptoKey key = Database.getTable(CryptoKey.class).newRecord();
        key.setAlias(Config.instance().getHostName() + ".k1");
        key = Database.getTable(CryptoKey.class).getRefreshed(key);
        if (key.getRawRecord().isNewRecord()){
            return null;
        }
        return key;
    }
}
