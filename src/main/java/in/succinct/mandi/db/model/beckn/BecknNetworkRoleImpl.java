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
}
