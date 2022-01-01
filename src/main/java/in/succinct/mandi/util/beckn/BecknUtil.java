package in.succinct.mandi.util.beckn;

import com.venky.cache.Cache;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.plugins.collab.db.model.CryptoKey;
import com.venky.swf.plugins.sequence.db.model.SequentialNumber;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Subscriber;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import org.apache.xmlbeans.impl.regex.Match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BecknUtil {
    public static String getNetworkParticipantId(){
        return Config.instance().getHostName();
    }
    public static String getSubscriberId(String domain, String type , String registryId){
        BecknNetwork network = BecknNetwork.find(registryId);
        return getSubscriberId(domain,type,network);
    }
    public static String getSubscriberId(String domain, String type , BecknNetwork network){
        if (network != null) {
            if (ObjectUtil.equals(type, Subscriber.SUBSCRIBER_TYPE_BAP)) {
                return network.getDeliveryBapSubscriberId();
            } else if (ObjectUtil.equals(type, Subscriber.SUBSCRIBER_TYPE_BPP)) {
                return network.getRetailBppSubscriberId();
            }
        }
        return String.format("%s.%s.%s",getNetworkParticipantId(),domain,type);
    }

    public static String getCryptoKeyId(BecknNetwork network){
        if (network != null){
            return network.getCryptoKeyId();
        }else {
            return BecknUtil.getNetworkParticipantId() + ".k" + BecknUtil.getCurrentKeyNumber();
        }
    }

    public static String getIdPrefix(){
        //return "./nic2004:52110/IND.std:080/";
        return "./retail.kirana/ind.blr/";
    }

    public static String getIdSuffix(){
        return getNetworkParticipantId();
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


    public static CryptoKey getSelfEncryptionKey(BecknNetwork network){
        CryptoKey encryptionKey = CryptoKey.find(getCryptoKeyId(network),CryptoKey.PURPOSE_ENCRYPTION);
        if (encryptionKey.getRawRecord().isNewRecord()){
            return null;
        }
        return encryptionKey;
    }
    public static CryptoKey getSelfKey(BecknNetwork network){
        CryptoKey key = CryptoKey.find(getCryptoKeyId(network) ,CryptoKey.PURPOSE_SIGNING);
        if (key.getRawRecord().isNewRecord()){
            return null;
        }
        return key;
    }

    public static long  getCurrentKeyNumber(){
        String sKeyNumber =  SequentialNumber.get("KEYS").getCurrentNumber();
        return Long.parseLong(sKeyNumber);
    }

}
