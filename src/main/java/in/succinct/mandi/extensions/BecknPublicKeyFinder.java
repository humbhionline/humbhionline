package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import in.succinct.beckn.Request;

import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Optional;

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
        //object.put("type","bpp");
        //object.put("domain","local-retail");

        JSONArray responses = new Call<JSONObject>().method(HttpMethod.POST).url(BecknUtil.getRegistryUrl() +"/lookup").input(object).inputFormat(InputFormat.JSON).
                header("Authorization",new Request().generateAuthorizationHeader(BecknUtil.getDomainId(),BecknUtil.getDomainId() +".k1"))
                .header("content-type", MimeType.APPLICATION_JSON.toString()).getResponseAsJson();

        if (responses != null && !responses.isEmpty()){
            Optional response = responses.stream().filter(o-> ObjectUtil.equals(((JSONObject)o).get("status"),"SUBSCRIBED"))
                    .findFirst();
            if (response.isPresent()){
                publicKeyHolder.set((String)((JSONObject)response.get()).get("signing_public_key"));
            }

        }
    }
}
