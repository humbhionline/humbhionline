package in.succinct.mandi.extensions;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.User;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.io.ModelReader;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.JSON;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import in.succinct.mandi.db.model.ServerNode;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;

public class RequestAuthenticator extends com.venky.swf.extensions.RequestAuthenticator {
    static{
        Registry.instance().registerExtension(Path.REQUEST_AUTHENTICATOR,new RequestAuthenticator());
    }

    @Override
    public void invoke(Object... context) {
        super.invoke(context);

        Path path = (Path)context[0];
        ObjectHolder<User> userObjectHolder = (ObjectHolder<User>)context[1];
        User user = userObjectHolder.get();
        if (user != null){
            return;
        }

        if (!ObjectUtil.equals(path.getProtocol(), MimeType.APPLICATION_JSON)){
            return;
        }
        if (ObjectUtil.equals(path.controllerPath(),"/users") && ObjectUtil.equals(path.action(),"current")){
            return;
        }

        String apiKey = path.getHeader("ApiKey");
        if (apiKey != null) {
            user = path.getUser("API_KEY", apiKey);
        }
        if (user == null){
            Application application = path.getApplication();
            if (application != null){
                ServerNode node = ServerNode.findNode(application.getAppId());
                if (node != null && !node.isSelf()){
                    JSONObject jsonObject = new Call<JSONObject>().url(node.getBaseUrl() + "/users/current").header("ApiKey",apiKey).header("content-type",MimeType.APPLICATION_JSON.toString()).method(HttpMethod.GET)
                            .getResponseAsJson();
                    if (jsonObject != null) {
                        jsonObject = (JSONObject) jsonObject.get("User");
                        ModelReader<User,JSONObject> reader = ModelIOFactory.getReader(User.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON));
                        reader.setInvalidReferencesAllowed(true);
                        user = reader.read(jsonObject);
                        user.setApiKey(apiKey);
                        userObjectHolder.set(user);
                        Database.getInstance().open(user);
                        Database.getInstance().getCache(user.getReflector()).remove(user.getRawRecord());// To ensure it is read consistenly.
                        Database.getInstance().getCache(user.getReflector()).add(user.getRawRecord());
                        return;
                    }
                }
            }
        }


        if (!ObjectUtil.equals(path.controllerPath(),"/login") || !ObjectUtil.equals(path.action(),"index")){
            return;
        }
        JSONObject input ;
        try {
            input = (JSONObject)JSONValue.parse(StringUtil.read(path.getInputStream()));
            if (input == null){
                return;
            }
            input = (JSONObject)input.get("User");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String phoneNumber = (String)input.get("PhoneNumber");
        if (ObjectUtil.isVoid(phoneNumber)){
            return;
        }
        phoneNumber = Phone.sanitizePhoneNumber(phoneNumber);
        String password = (String)input.get("Password");
        if (ObjectUtil.isVoid(password)){
            return;
        }
        user = path.getUser("PHONE_NUMBER",phoneNumber);
        if (user != null && !user.authenticate(password)){
            user = null;
        }
        if (user != null){
            userObjectHolder.set(user);
        }

    }
}
