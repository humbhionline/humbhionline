package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.beckn.tasks.BecknTask;
import com.venky.swf.routing.Config;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class BecknNetworkRoleImpl extends ModelImpl<BecknNetworkRole> {
    public BecknNetworkRoleImpl(BecknNetworkRole proxy){
        super(proxy);
    }
    public Subscriber getSubscriber(){
        BecknNetworkRole role = getProxy();
        BecknNetwork network = role.getBecknNetwork();
        CryptoKey encKey = CryptoKey.find(network.getCryptoKeyId(),CryptoKey.PURPOSE_ENCRYPTION);
        CryptoKey signKey = CryptoKey.find(network.getCryptoKeyId(),CryptoKey.PURPOSE_SIGNING);
        NetworkAdaptor networkAdaptor = NetworkAdaptorFactory.getInstance().getAdaptor(network.getNetworkId());
        return new Subscriber() {
            {
                setSubscriberId(network.getSubscriberId());
                setSubscriberUrl(String.format("%s/%s/%s", Config.instance().getServerBaseUrl() , network.getNetworkId(),
                        role.getRole().equals(Subscriber.SUBSCRIBER_TYPE_BPP) ? "bpp" : "network"));
                setUniqueKeyId(network.getCryptoKeyId());
                setAlias(network.getCryptoKeyId());
                setDomain(role.getDomain());
                setType(role.getRole());
                setEncrPublicKey(encKey.getPublicKey());
                setSigningPublicKey(signKey.getPublicKey());
                setValidFrom(signKey.getCreatedAt());
                setValidTo(new Date(signKey.getUpdatedAt().getTime() + (long) (10L * 365.25D * 24L * 60L * 60L * 1000L)));


                setCity(networkAdaptor.getWildCard());
                setCountry(networkAdaptor.getCountry());
                networkAdaptor.getSubscriptionJson(this);
            }
            /*
            Map<String,Class<? extends BecknTask>> map = new HashMap<String,Class<? extends BecknTask>>(){{
                put("search", Search.class);
                put("select", Select.class);
                put("init", Init.class);
                put("confirm", Confirm.class);
                put("status", Status.class);
                put("track", Track.class);
                put("cancel", Cancel.class);
                put("update", Update.class);
                put("rating", Rating.class);
                put("support", Support.class);
                put("get_rating_categories", RatingCategory.class);
                put("get_feedback_categories", FeedbackCategory.class);
                put("get_cancellation_reasons", CancellationReason.class);
                put("get_return_reasons", ReturnReason.class);


                put("on_search", GenericCallBackRecorder.class);
                put("on_select", GenericCallBackRecorder.class);
                put("on_init", GenericCallBackRecorder.class);
                put("on_confirm", GenericCallBackRecorder.class);
                put("on_status", OnStatus.class);
                put("on_cancel", GenericCallBackRecorder.class);

            }};
            */

            @Override
            public Mq getMq() {
                if (!network.isMqSupported()){
                    return null;
                }
                return new Mq() {

                    @Override
                    public String getProvider() {
                        return network.getMqProvider();
                    }

                    @Override
                    public String getHost() {
                        return network.getMqHost();
                    }

                    @Override
                    public String getPort() {
                        return network.getMqPort() == null ? null : String.valueOf(network.getMqPort());
                    }

                };
            }

            @Override
            public Set<String> getSupportedActions() {
                return new HashSet<String>() {{
                    addAll(Subscriber.BAP_ACTION_SET);
                    addAll(Subscriber.BPP_ACTION_SET);
                }};

            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends BecknTask> getTaskClass(String action) {
                return null;
            }
        };
    }
}
