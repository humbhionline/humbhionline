package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.routing.Config;
import in.succinct.mandi.db.model.ServerNode;

public class BeforeValidateServerNode extends BeforeModelValidateExtension<ServerNode> {
    static {
        registerExtension(new BeforeValidateServerNode());
    }
    @Override
    public void beforeValidate(ServerNode model) {
        if (ObjectUtil.isVoid(model.getBaseUrl())){
            throw new RuntimeException("Base Url is mandatory");
        }
        if (!model.getBaseUrl().startsWith("https")){
            if (!Config.instance().isDevelopmentEnvironment()){
                throw new RuntimeException("Base url must be https");
            }
        }
    }
}
