package in.succinct.mandi.util;

import com.venky.core.collections.IgnoreCaseMap;
import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.UserEmail;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.db.model.io.ModelReader;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.Record;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.User;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class InternalNetwork {
    public static Map<String,String> extractHeaders(Path path){
        Map<String,String> headers = new IgnoreCaseMap<>();
        Cookie[] cookies = path.getRequest().getCookies();
        if (cookies != null && cookies.length > 0){
            for (int i = 0 ; i <cookies.length ; i  ++ ){
                if (ObjectUtil.equals(cookies[i].getName(),"JSESSIONID")){
                    headers.put("Cookie", String.format("JSESSIONID=%s",cookies[i].getValue()));
                    break;
                }
            }
        }
        for (String header : new String[] {"Authorization","Content-Type","ApiKey","Lat","Lng","User-Agent","Accept-Encoding","Referer","KeepAlive"}) {
            String v = path.getHeader(header);
            if (!ObjectUtil.isVoid(v)) {
                headers.put(header,v);
            }
        }
        ServerNode node = ServerNode.selfNode();
        if (node != null){
            headers.put("Authorization",node.getAuthorizationHeader());
        }

        return headers;

    }
    private static boolean isRemoteUserSession(Path path){
        String cookie = path.getHeader("Cookie");
        boolean hasSessionId = cookie != null && cookie.startsWith("JSESSIONID");
        return hasSessionId || !ObjectUtil.isVoid(path.getHeader("ApiKey"));
    }
    public static ServerNode getRemoteServer(Path path){
        return getRemoteServer(path,true);
    }
    public static ServerNode getRemoteServer(Path path, boolean ensureNotSelf){
        Application app = path.getApplication();
        if (app != null){
            ServerNode node = ServerNode.findNode(app.getAppId());
            if (node != null && (!ensureNotSelf || !node.isSelf()) ){
                if (node.isApproved()) {
                    return node;
                }else {
                    throw new RuntimeException("Node " + node.getClientId() + " is not yet approved to make calls on the network. ");
                }
            }
        }
        return null;
    }
    public static User getRemoteNetworkUser(Path path){
        Application application = path.getApplication();
        User user = null;
        ServerNode node = getRemoteServer(path,true);
        if (node != null){
            if (isRemoteUserSession(path)) {
                JSONObject jsonObject = new Call<JSONObject>().url(node.getBaseUrl() + "/users/current").
                        headers(extractHeaders(path)).
                        header("Content-Type",MimeType.APPLICATION_JSON.toString()).
                        header("KeepAlive","Y").
                        method(HttpMethod.GET).getResponseAsJson();
                if (jsonObject != null) {
                    JSONObject jsonUser = (JSONObject) jsonObject.get("User");
                    user = loadUserToCache(jsonUser);
                }
            }
        }
        return user;
    }

    public static User loadUserToCache(JSONObject jsonUser) {

        ModelReader<User,JSONObject> reader = ModelIOFactory.getReader(User.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON));
        reader.setInvalidReferencesAllowed(true);
        User user = reader.read(jsonUser);

        Database.getInstance().getCache(user.getReflector()).remove(user.getRawRecord());// To ensure it is read consistenly.
        Database.getInstance().getCache(user.getReflector()).add(user.getRawRecord());
        for (Expression e : user.getReflector().getUniqueKeyConditions(user)){
            SequenceSet<Record> records = new SequenceSet<>();
            records.add(user.getRawRecord());
            Database.getInstance().getCache(user.getReflector()).setCachedResult(e,records);
        }

        loadModelObjectsToCache(jsonUser,user, UserRole.class);
        loadModelObjectsToCache(jsonUser,user, UserPhone.class);
        loadModelObjectsToCache(jsonUser,user, UserEmail.class);
        loadModelObjectsToCache(jsonUser,user, Device.class);

        return user;
    }

    private static <M extends Model> void loadModelObjectsToCache(JSONObject jsonUser, User user, Class<M> modelClass) {
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
    public static <T  extends ServerNode> List<ServerNode> getNodes(){
        return getNodes(serverNode -> true);
    }
    public static  List<ServerNode> getNodes(Predicate<ServerNode> predicate){
        List<ServerNode> nodes = new Select().from(ServerNode.class).execute(ServerNode.class, Select.MAX_RECORDS_ALL_RECORDS,
                record -> record.isApproved() && predicate.test(record));
        return nodes;
    }

    public static boolean isCurrentServerRegistry(){
        return ObjectUtil.equals(Config.instance().getServerBaseUrl(),getRegistryUrl());
    }

    public static String getRegistryUrl(){
        String hboRegistryUrl = Config.instance().getProperty("hbo.registry.url");
        if (ObjectUtil.isVoid(hboRegistryUrl)){
            throw new RuntimeException("Registry url not defined");
        }
        return hboRegistryUrl;
    }

}
