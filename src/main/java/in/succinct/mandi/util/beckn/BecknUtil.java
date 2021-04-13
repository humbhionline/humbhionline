package in.succinct.mandi.util.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.routing.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknUtil {
    public static String getDomainId(){
        return Config.instance().getHostName();
    }
    public static String getIdPrefix(){
        return "./retail.kirana/ind.blr/";
    }
    public static String getIdSuffix(){
        return getDomainId();
    }
    public enum Entity {
        fulfillment,
        category,
        provider,
        provider_category,
        provider_location,
        item,
        catalog,
    }
    public static String getBecknId(String localUniqueId,Entity becknEntity){
        StringBuilder builder = new StringBuilder();
        builder.append(getIdPrefix());
        if (!ObjectUtil.isVoid(localUniqueId)){
            builder.append(localUniqueId).append("@");
        }
        builder.append(getIdSuffix());
        if (becknEntity != null){
            builder.append(".").append(becknEntity.toString());
        }
        return builder.toString();
    }
    public static String getLocalUniqueId(String beckId, Entity becknEntity){
        String  pattern = String.format("^%s/(.*)@%s[.]%s$",getIdPrefix(),getIdSuffix(),becknEntity == null ? "" : "."+becknEntity.toString());
        Matcher matcher = Pattern.compile(pattern).matcher(beckId);
        List<String> ids = new ArrayList<>();
        matcher.results().forEach(mr->{
            ids.add(mr.group(1));
        });
        if (ids.size() == 1){
            return ids.get(0);
        }
        throw new RuntimeException("Id not formated as expected!");
    }
}
