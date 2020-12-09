package in.succinct.mandi.extensions;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.User;
import com.venky.swf.path.Path;
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
        if (!ObjectUtil.equals(path.controllerPath(),"/login") || !ObjectUtil.equals(path.action(),"index")){
            return;
        }
        JSONObject input ;
        try {
            input = (JSONObject)JSONValue.parse(StringUtil.read(path.getInputStream()));
            input = (JSONObject)input.get("User");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String phoneNumber = (String)input.get("PhoneNumber");
        if (ObjectUtil.isVoid(phoneNumber)){
            return;
        }
        String password = (String)input.get("Password");
        if (ObjectUtil.isVoid(password)){
            return;
        }
        user = path.getUser("PHONE_NUMBER",phoneNumber);
        if (!user.authenticate(password)){
            user = null;
        }
        if (user != null){
            userObjectHolder.set(user);
        }

    }
}
