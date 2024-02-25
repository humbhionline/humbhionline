package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.routing.Config;
import in.succinct.mandi.db.model.beckn.BecknNetwork;

public class BeforeValidateBecknNetwork extends BeforeModelValidateExtension<BecknNetwork> {
    static {
        registerExtension(new BeforeValidateBecknNetwork());
    }
    @Override
    public void beforeValidate(BecknNetwork model) {
        if (ObjectUtil.isVoid(model.getSubscriberId())){
            model.setSubscriberId(Config.instance().getHostName());
        }
    }
}
