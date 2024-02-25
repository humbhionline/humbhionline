package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.routing.Config;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.db.model.beckn.BecknNetworkRole;
import in.succinct.onet.core.adaptor.NetworkAdaptor.Domain;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;

public class BeforeValidateBecknNetworkRole extends BeforeModelValidateExtension<BecknNetworkRole> {
    static {
        registerExtension(new BeforeValidateBecknNetworkRole());
    }
    @Override
    public void beforeValidate(BecknNetworkRole model) {
        if (ObjectUtil.isVoid(model.getDomain())){
            return;
        }
        Domain domain  = NetworkAdaptorFactory.getInstance().getAdaptor(model.getBecknNetwork().getNetworkId()).getDomains().get(model.getDomain());
        if (domain == null){
            throw new RuntimeException("Not a domain on the network");
        }
        

    }
}
