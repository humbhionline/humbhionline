package in.succinct.mandi.db.model.beckn;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.beckn.messaging.BapSubscriber;
import com.venky.swf.plugins.beckn.messaging.BppSubscriber;
import com.venky.swf.plugins.beckn.messaging.Mq;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.beckn.tasks.BecknTask;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Organization;
import in.succinct.beckn.Subscriber.Domains;
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
import in.succinct.mandi.util.CompanyUtil;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;
import in.succinct.plugins.ecommerce.db.model.participation.Company;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BecknNetworkImpl extends ModelImpl<BecknNetwork> {
    public BecknNetworkImpl(BecknNetwork proxy) {
        super(proxy);
    }

    public String getRegistryUrl() {
        return NetworkAdaptorFactory.getInstance().getAdaptor(getProxy().getNetworkId()).getRegistryUrl();
    }



    public NetworkAdaptor getNetworkAdaptor() {
        return NetworkAdaptorFactory.getInstance().getAdaptor(getProxy().getNetworkId());
    }


    public Subscriber getBppSubscriber() {
        BecknNetwork network = getProxy();
        Domains domains = new Domains();
        NetworkAdaptor adaptor = network.getNetworkAdaptor();
        CryptoKey encKey = CryptoKey.find(network.getCryptoKeyId(), CryptoKey.PURPOSE_ENCRYPTION);
        CryptoKey signKey = CryptoKey.find(network.getCryptoKeyId(), CryptoKey.PURPOSE_SIGNING);

        network.getBecknNetworkRoles().forEach(becknNetworkRole -> {
            if (ObjectUtil.equals(Subscriber.SUBSCRIBER_TYPE_BPP, becknNetworkRole.getRole())) {
                Domain domain = adaptor.getDomains().get(becknNetworkRole.getDomain());
                if (domain == null) {
                    throw new RuntimeException(String.format("%s is not a valid domain in the network %s", becknNetworkRole.getDomain(), getProxy().getNetworkId()));
                }
                domains.add(becknNetworkRole.getDomain());
            }
        });
        return new Subscriber() {
            {
                setSubscriberId(network.getSubscriberId());
                setUniqueKeyId(network.getCryptoKeyId());
                setAlias(network.getCryptoKeyId());
                setDomains(domains);
                setType(Subscriber.SUBSCRIBER_TYPE_BPP);
                setSubscriberUrl(String.format("%s/%s/%s", Config.instance().getServerBaseUrl(), network.getNetworkId(), "bpp"));
                setEncrPublicKey(encKey.getPublicKey());
                setSigningPublicKey(signKey.getPublicKey());
                setValidFrom(signKey.getCreatedAt());
                setValidTo(new Date(signKey.getUpdatedAt().getTime() + (long) (10L * 365.25D * 24L * 60L * 60L * 1000L)));
                setCity(adaptor.getWildCard());
                setCountry(adaptor.getCountry());
                Organization organization = new Organization();
                Company mandi = CompanyUtil.getCompany();
                organization.setName(mandi.getName());
                organization.setDateOfIncorporation(mandi.getDateOfIncorporation());
                setOrganization(organization);
                adaptor.getSubscriptionJson(this); // Actually creates the crypto keys entries
            }

            Map<String,Class<? extends BecknTask>> map = new HashMap<>() {{
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
            /*
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
                if (!network.isMqSupported()) {
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
                    addAll(Subscriber.BPP_ACTION_SET);
                }};

            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends BecknTask> getTaskClass(String action) {
                return map.get(action);
            }
        };
    }

    public Subscriber getBapSubscriber() {
        BecknNetwork network = getProxy();
        Domains domains = new Domains();
        NetworkAdaptor adaptor = network.getNetworkAdaptor();
        CryptoKey encKey = CryptoKey.find(network.getCryptoKeyId(), CryptoKey.PURPOSE_ENCRYPTION);
        CryptoKey signKey = CryptoKey.find(network.getCryptoKeyId(), CryptoKey.PURPOSE_SIGNING);

        network.getBecknNetworkRoles().forEach(becknNetworkRole -> {
            if (ObjectUtil.equals(Subscriber.SUBSCRIBER_TYPE_BAP, becknNetworkRole.getRole())) {
                Domain domain = adaptor.getDomains().get(becknNetworkRole.getDomain());
                if (domain == null) {
                    throw new RuntimeException(String.format("%s is not a valid domain in the network %s", becknNetworkRole.getDomain(), getProxy().getNetworkId()));
                }
                domains.add(becknNetworkRole.getDomain());
            }
        });
        return new Subscriber() {
            {
                setSubscriberId(network.getSubscriberId());
                setUniqueKeyId(network.getCryptoKeyId());
                setAlias(network.getCryptoKeyId());
                setDomains(domains);
                setType(Subscriber.SUBSCRIBER_TYPE_BAP);
                setSubscriberUrl(String.format("%s/%s/%s", Config.instance().getServerBaseUrl(), network.getNetworkId(), "network"));
                setEncrPublicKey(encKey.getPublicKey());
                setSigningPublicKey(signKey.getPublicKey());
                setValidFrom(signKey.getCreatedAt());
                setValidTo(new Date(signKey.getUpdatedAt().getTime() + (long) (10L * 365.25D * 24L * 60L * 60L * 1000L)));
                setCity(adaptor.getWildCard());
                setCountry(adaptor.getCountry());
                Organization organization = new Organization();
                Company mandi = CompanyUtil.getCompany();
                organization.setName(mandi.getName());
                organization.setDateOfIncorporation(mandi.getDateOfIncorporation());
                setOrganization(organization);

                adaptor.getSubscriptionJson(this);
            }


            @Override
            public Mq getMq() {
                if (!network.isMqSupported()) {
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
                return Subscriber.BAP_ACTION_SET;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends BecknTask> getTaskClass(String action) {
                return null;
            }
        };
    }

    public void subscribe(){
        if (getProxy().getRawRecord().isNewRecord()){
            return;
        }
        BecknNetwork network = getProxy();
        NetworkAdaptor adaptor = getNetworkAdaptor();
        Subscriber bppSubscriber = getBppSubscriber();
        Subscriber bapSubscriber = getBapSubscriber();
        adaptor.subscribe(bppSubscriber);
        adaptor.subscribe(bapSubscriber);
        if (network.isMqSupported()){
            new BppSubscriber(bppSubscriber).registerSubscriber();
            new BapSubscriber(bapSubscriber).registerSubscriber();
        }
    }
}
