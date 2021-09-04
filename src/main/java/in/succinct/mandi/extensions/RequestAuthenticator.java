package in.succinct.mandi.extensions;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.User;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import in.succinct.mandi.util.InternalNetwork;
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
            user = InternalNetwork.getRemoteNetworkUser(path);
            if (user != null) {
                user.setApiKey(apiKey);
                userObjectHolder.set(user);
                Database.getInstance().open(user); //If I don't do this. Path will read session's user_id (which is null) and try to load it from db,
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
