package in.succinct.mandi.controller;

import com.venky.swf.path.Path;
import in.succinct.beckn.Subscriber;
import in.succinct.beckn.Subscriber.Domains;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.onet.core.adaptor.NetworkAdaptor;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.adaptor.NetworkAdaptor.DomainCategory;

public class NetworkController extends in.succinct.bap.shell.controller.NetworkController  {
    public NetworkController(Path path) {
        super(path);
    }

    public NetworkAdaptor getNetworkAdaptor() {
        BecknNetwork network = BecknNetwork.findByUrl(getPath().controllerPath());
        return network.getNetworkAdaptor();
    }

    public Subscriber getSubscriber(){
        BecknNetwork network = BecknNetwork.findByUrl(getPath().controllerPath());
        Subscriber subscriber = network.getBapSubscriber();
        Domains domains = subscriber.getDomains();
        if (subscriber.getDomains().size() > 1){
            for (String domain : domains) {
                Domain  d = network.getNetworkAdaptor().getDomains().get(domain);
                if (d.getDomainCategory() == DomainCategory.BUY_MOVABLE_GOODS){
                    subscriber.setDomain(d.getId());
                    break;
                }
            }
        }else {
            subscriber.setDomain(subscriber.getDomains().get(0));
        }
        return subscriber;
    }


}
