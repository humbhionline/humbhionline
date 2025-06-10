package in.succinct.mandi.util.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.routing.Config;
import in.succinct.mandi.db.model.beckn.BecknNetwork;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknUtil {

    public static String getCryptoKeyId(BecknNetwork network){
        if (network != null){
            return String.format("%s.%s",network.getSubscriberId(),network.getCryptoKeyId());
        }else {
            return String.format("%s.%s",Config.instance().getHostName(),"k0");
        }
    }

    public static String getIdPrefix(){
        return "./";
    }

    public static String getIdSuffix(){
        return Config.instance().getHostName();
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
        order,
        stop,
    }
    public static String getBecknId(Long localUniqueId,Entity becknEntity){
        return getBecknId(String.valueOf(localUniqueId),becknEntity);
    }
    public static String getBecknId(String localUniqueId,Entity becknEntity){
        return getBecknId(getIdPrefix(),localUniqueId, getIdSuffix(), becknEntity);
    }
    public static String getLocalUniqueId(String beckId, Entity becknEntity) {
        String pattern = "^(.*/)(.*)@(.*)\\." + becknEntity + "$";
        Matcher matcher = Pattern.compile(pattern).matcher(beckId);
        if (matcher.find()){
            return matcher.group(2);
        }
        return "-1";
    }
    public static String getBecknId(String prefix, String localUniqueId, String suffix , Entity becknEntity){
        StringBuilder builder = new StringBuilder();
        builder.append(prefix);
        if (!ObjectUtil.isVoid(localUniqueId)){
            builder.append(localUniqueId);
        }else {
            builder.append(0);
        }
        builder.append("@");
        builder.append(suffix);
        if (becknEntity != null){
            builder.append(".").append(becknEntity);
        }
        return builder.toString();
    }


}
