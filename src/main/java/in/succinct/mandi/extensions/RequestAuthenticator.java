package in.succinct.mandi.extensions;

import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Registry;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.DATA_TYPE;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.User;
import com.venky.swf.db.model.UserEmail;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.io.ModelReader;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.Record;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.JSON;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import in.succinct.mandi.db.model.ServerNode;
import org.json.simple.JSONArray;
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
                    JSONObject jsonObject = new Call<JSONObject>().url(node.getBaseUrl() + "/users/current").headers(path.getHeaders()).header("content-type",MimeType.APPLICATION_JSON.toString()).method(HttpMethod.GET)
                            .getResponseAsJson();
                    if (jsonObject != null) {
                        JSONObject jsonUser = (JSONObject) jsonObject.get("User");
                        ModelReader<User,JSONObject> reader = ModelIOFactory.getReader(User.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON));
                        reader.setInvalidReferencesAllowed(true);
                        user = reader.read(jsonUser);
                        user.setApiKey(apiKey);
                        userObjectHolder.set(user);
                        Database.getInstance().open(user);
                        loadUser(jsonUser,user);
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

    private void loadUser(JSONObject jsonUser, User user) {
        Database.getInstance().getCache(user.getReflector()).remove(user.getRawRecord());// To ensure it is read consistenly.
        Database.getInstance().getCache(user.getReflector()).add(user.getRawRecord());
        for (Expression e : user.getReflector().getUniqueKeyConditions(user)){
            SequenceSet<Record> records = new SequenceSet<>();
            records.add(user.getRawRecord());
            Database.getInstance().getCache(user.getReflector()).setCachedResult(e,records);
        }
        loadModelObjectsToCache(jsonUser,user,UserRole.class);
        loadModelObjectsToCache(jsonUser,user,UserPhone.class);
        loadModelObjectsToCache(jsonUser,user, UserEmail.class);
        loadModelObjectsToCache(jsonUser,user,Device.class);

    }

    private <M extends Model> void loadModelObjectsToCache(JSONObject jsonUser, User user, Class<M> modelClass) {
        ModelReflector<M> ref = ModelReflector.instance(modelClass);
        JSONArray jsonModelObjects = (JSONArray) jsonUser.get(StringUtil.pluralize(modelClass.getSimpleName()));
        if (jsonModelObjects != null){
            SequenceSet<Record> rawRecords = new SequenceSet<>();
            for (Object o : jsonModelObjects){
                JSONObject jsonModel = (JSONObject) o;
                ModelReader<M,JSONObject> reader = ModelIOFactory.getReader(modelClass,
                        FormatHelper.getFormatClass(MimeType.APPLICATION_JSON));
                reader.setInvalidReferencesAllowed(true);
                M model = reader.read(jsonModel);
                ref.set(model,"USER_ID",user.getId());
                rawRecords.add(model.getRawRecord());
                Database.getInstance().getCache(model.getReflector()).remove(model.getRawRecord());
                Database.getInstance().getCache(model.getReflector()).add(model.getRawRecord());
            }
            Expression where = new Expression(ref.getPool(), "USER_ID", Operator.EQ,user.getId());
            Database.getInstance().getCache(ref).setCachedResult(where,rawRecords);
        }
    }

}
