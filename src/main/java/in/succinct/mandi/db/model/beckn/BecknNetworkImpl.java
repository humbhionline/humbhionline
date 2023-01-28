package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.beckn.tasks.BecknTask;
import com.venky.swf.routing.Config;
import in.succinct.mandi.agents.beckn.BecknAsyncTask;
import in.succinct.mandi.agents.beckn.Cancel;
import in.succinct.mandi.agents.beckn.CancellationReason;
import in.succinct.mandi.agents.beckn.Confirm;
import in.succinct.mandi.agents.beckn.FeedbackCategory;
import in.succinct.mandi.agents.beckn.Init;
import in.succinct.mandi.agents.beckn.Rating;
import in.succinct.mandi.agents.beckn.RatingCategory;
import in.succinct.mandi.agents.beckn.ReturnReason;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.agents.beckn.Select;
import in.succinct.mandi.agents.beckn.Status;
import in.succinct.mandi.agents.beckn.Support;
import in.succinct.mandi.agents.beckn.Track;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.agents.beckn.bap.delivery.GenericCallBackRecorder;
import in.succinct.mandi.agents.beckn.bap.delivery.OnStatus;
import in.succinct.mandi.util.beckn.BecknUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BecknNetworkImpl extends ModelImpl<BecknNetwork> {
    public BecknNetworkImpl(BecknNetwork proxy){
        super(proxy);
    }

    public Subscriber getRetailBppSubscriber(){
        BecknNetwork network = getProxy();
        return new Subscriber() {
            {
                setSubscriberId(network.getRetailBppSubscriberId());
                setSubscriberUrl(String.format("%s/%s", Config.instance().getServerBaseUrl() , network.getRetailBppUrl()));
                setUniqueKeyId(network.getCryptoKeyId());
                setDomain(BecknUtil.LOCAL_RETAIL);

            }
            Map<String,Class<? extends BecknAsyncTask>> map = new HashMap<String,Class<? extends BecknAsyncTask>>(){{
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


            }};


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
                return map.keySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends BecknAsyncTask> getTaskClass(String action) {
                return map.get(action);
            }
        };
    }

    public Subscriber getDeliveryBapSubscriber(){
        BecknNetwork network = getProxy();
        return new Subscriber() {
            Map<String,Class<? extends BecknTask>> map = new HashMap<String,Class<? extends BecknTask>>(){{
                put("on_search", GenericCallBackRecorder.class);
                put("on_select", GenericCallBackRecorder.class);
                put("on_init", GenericCallBackRecorder.class);
                put("on_confirm", GenericCallBackRecorder.class);
                put("on_status", OnStatus.class);
                put("on_cancel", GenericCallBackRecorder.class);
                //put("track", Track.class);
                //put("update", Update.class);
            }};
            {
                setSubscriberId(network.getDeliveryBapSubscriberId());
                setSubscriberUrl(String.format("%s/%s", Config.instance().getServerBaseUrl() , network.getDeliveryBapUrl()));
                setUniqueKeyId(network.getDeliveryBapKeyId());
                setDomain(BecknUtil.LOCAL_DELIVERY);
            }

            @Override
            public Mq getMq() {
                if (network.isMqSupported()){
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
                return map.keySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends BecknTask> Class<T> getTaskClass(String action) {
                return (Class<T>)map.get(action);
            }
        };
    }
}
