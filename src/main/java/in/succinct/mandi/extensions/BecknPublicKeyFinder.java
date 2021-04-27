package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.beckn.Request;

import org.json.simple.JSONObject;

public class BecknPublicKeyFinder implements Extension {
    static {
        Registry.instance().registerExtension("beckn.public.key.get",new BecknPublicKeyFinder());
    }

    @Override
    public void invoke(Object... context) {
        String subscriber_id = (String)context[0];
        String uniqueKeyId = (String)context[1];
        ObjectHolder<String> publicKeyHolder = (ObjectHolder<String>) context[2];

        JSONObject object = new JSONObject();
        object.put("subscriber_id",subscriber_id);
        object.put("type","bpp");
        object.put("domain","local-retail");

        JSONObject response = new Call<JSONObject>().url("https://registry.beckn.succinct.in/lookup").input(object).inputFormat(InputFormat.JSON).
                header("Authorization",new Request().generateAuthorizationHeader(subscriber_id,uniqueKeyId)).getResponseAsJson();

        if (ObjectUtil.equals(response.get("status"),"SUBSCRIBED")){
            publicKeyHolder.set((String)response.get("signing_public_key"));
        }
    }
}
