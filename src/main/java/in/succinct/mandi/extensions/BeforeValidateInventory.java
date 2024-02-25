package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.db.model.application.Event;
import in.succinct.beckn.Catalog;
import in.succinct.beckn.Context;
import in.succinct.beckn.Message;
import in.succinct.beckn.Request;
import in.succinct.beckn.Subscriber;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.onet.core.adaptor.NetworkAdaptor;

import java.util.Date;
import java.util.UUID;

public class BeforeValidateInventory extends BeforeModelValidateExtension<Inventory> {
    static {
        registerExtension(new BeforeValidateInventory());
    }
    @Override
    public void beforeValidate(Inventory model) {
        if (model.getRawRecord().isFieldDirty("ENABLED")){
            Catalog catalog = new Catalog();
            Search.updateCatalog(catalog,model);
            for (BecknNetwork network : BecknNetwork.all()) {
                raiseEvent(model.isEnabled() ? "activate" : "deactivate" , catalog, network.getBppSubscriber(),network.getNetworkAdaptor());
                //Publish to search provider.
            }
        }
    }


    public void raiseEvent(String operation, Catalog catalog, Subscriber bppSubscriber, NetworkAdaptor networkAdaptor){
        Event event = Event.find("catalog_" + operation);
        Request request = new Request();
        Context context = new Context();
        request.setContext(context);
        request.setMessage(new Message());
        request.getMessage().setCatalog(catalog);
        context.setBppId(bppSubscriber.getSubscriberId());
        context.setBppUri(bppSubscriber.getSubscriberUrl());
        context.setTransactionId(UUID.randomUUID().toString());
        context.setMessageId(UUID.randomUUID().toString());
        context.setDomain(bppSubscriber.getDomain());
        context.setCountry(bppSubscriber.getCountry());
        context.setCoreVersion(networkAdaptor.getCoreVersion());
        context.setTimestamp(new Date());
        context.setNetworkId(networkAdaptor.getId());
        context.setCity(bppSubscriber.getCity());
        context.setAction("on_search");
        context.setTtl(60);
        for (in.succinct.beckn.Provider provider : catalog.getProviders()){
            provider.setTag("general_attributes","catalog.indexer.reset","N");
            provider.setTag("general_attributes","catalog.indexer.operation",operation);
        }

        event.raise(request);

    }
}
