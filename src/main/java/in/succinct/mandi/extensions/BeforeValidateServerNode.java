package in.succinct.mandi.extensions;

import com.venky.core.security.Crypt;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.integration.api.Call;
import com.venky.swf.routing.Config;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.util.InternalNetwork;
import org.json.JSONObject;

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
        if (model.getRawRecord().isFieldDirty("ENCRYPTION_PUBLIC_KEY") && !ObjectUtil.isVoid(model.getEncryptionPublicKey())){
            Crypt.getInstance().getPublicKey(Request.ENCRYPTION_ALGO,model.getEncryptionPublicKey());
        }
        if (model.getRawRecord().isFieldDirty("SIGNING_PUBLIC_KEY")  && !ObjectUtil.isVoid(model.getSigningPublicKey())){
            Crypt.getInstance().getPublicKey(Request.SIGNATURE_ALGO,model.getSigningPublicKey());
        }

        if (model.getRawRecord().isFieldDirty("CLIENT_ID") && ObjectUtil.isVoid(model.getClientId())){
            Application application = Database.getTable(Application.class).newRecord();
            application.setAppId(model.getClientId());
            application = Database.getTable(Application.class).getRefreshed(application);
            application.setChangeSecret(model.getClientSecret());
            application.save();
        }
    }


}
