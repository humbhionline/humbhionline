package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.TemplateLoader;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.PinCode;
import com.venky.swf.plugins.collab.db.model.config.Role;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.mobilesignup.db.model.SignUp;
import com.venky.swf.plugins.security.db.model.UserRole;
import com.venky.swf.plugins.templates.db.model.alerts.Device;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Select.ResultFilter;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.MobileMeta;
import in.succinct.mandi.db.model.SavedAddress;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.AadharEKyc;
import in.succinct.mandi.util.InternalNetwork;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class UsersController extends com.venky.swf.plugins.collab.controller.UsersController implements TemplateLoader {
    public UsersController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View current() {
        if (getSessionUser() == null){
            return blank();
        }else {
            return show(getSessionUser());
        }
    }

    @RequireLogin(false)
    public View internal_search(){
        ServerNode callingNode = InternalNetwork.getRemoteServer(getPath());
        if (callingNode == null){
            throw new RuntimeException("Unauthorized application");
        }
        return super.search();
    }

    @Override
    protected ResultFilter<com.venky.swf.db.model.User> getFilter() {
        ServerNode remote = InternalNetwork.getRemoteServer(getPath());
        if (remote != null) {
            return record -> true;
        }else {
            return super.getFilter();
        }
    }

    @Override
    @RequireLogin
    public View html(String path) {
        return html(path, false);
    }

    public View verify(long id) {
        User user = Database.getTable(User.class).get(id);
        user.setVerified(true);
        user.save();
        return IntegrationAdaptor.instance(User.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).createResponse(getPath(),
                user,user.getReflector().getFields(),new HashSet<>(),getIncludedModelFields());
    }

    public View doAadharKyc() throws Exception {
        HttpServletRequest request = getPath().getRequest();
        if (!request.getMethod().equalsIgnoreCase("POST")) {
            throw new RuntimeException("Cannot call save in any other method other than POST");
        }
        Map<String,Object> formFields = getPath().getFormFields();
        User verifyingUser = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);
        User user = null;

        if (!formFields.isEmpty()) {
            String sFileData = (String) formFields.get("zipfile");
            InputStream in = null;
            String[] expectedPrefixes = new String[] {"data:application/zip;base64,", "data:application/x-zip-compressed;base64,"};
            for (String expectedPrefix : expectedPrefixes){
                if (sFileData.startsWith(expectedPrefix)) {
                    in = new ByteArrayInputStream(Base64.getDecoder().decode(sFileData.substring(expectedPrefix.length())));
                    break;
                }
            }
            if (in == null) {
                throw new RuntimeException("Nothing uploaded!");
            }
            String password = (String) formFields.get("password");
            String id = (String)formFields.get("id");
            if (!ObjectUtil.isVoid(id)){
                user = Database.getTable(User.class).get(Long.valueOf(id));
            }
            if (user != null ){
                if (user.getId() != verifyingUser.getId() && !verifyingUser.isStaff()){
                    user = null;
                }
            }
            if (user == null){
                throw new RuntimeException("Don't know which user you are verifying for?");
            }

            AadharEKyc.AadharData data = AadharEKyc.getInstance().parseZip(in, password);
            if (data != null) {
                if (!ObjectUtil.isVoid(user.getEmail())) {
                    data.validateEmail(user.getEmail());
                }
                if (!ObjectUtil.isVoid(user.getPhoneNumber())) {
                    data.validatePhone(user.getPhoneNumber());
                    user.setVerified(true);
                    user.setTxnProperty("verifiedViaKyc",true);
                }
                user.setLongName(data.get(AadharEKyc.AadharData.NAME));
                user.setDateOfBirth(new Date(data.getDateOfBirth().getTime()));
                user.setAddressLine1(data.get(AadharEKyc.AadharData.HOUSE));
                user.setAddressLine2(data.get(AadharEKyc.AadharData.STREET));
                user.setAddressLine3(data.get(AadharEKyc.AadharData.LOCALITY));
                user.setAddressLine4(data.get(AadharEKyc.AadharData.POST_OFFICE));
                user.setCountryId(Country.findByName("India").getId());
                State state = State.findByCountryAndName(user.getCountryId(), data.get(AadharEKyc.AadharData.STATE));
                if (state != null) {
                    user.setStateId(state.getId());
                }
                City city = City.findByStateAndName(user.getStateId(), data.get(AadharEKyc.AadharData.DISTRICT));
                if (city == null) {
                    city = City.findByStateAndName(user.getStateId(), data.get(AadharEKyc.AadharData.LOCALITY));
                }
                if (city != null) {
                    user.setCityId(city.getId());
                }


                PinCode pinCode = PinCode.find(data.get(AadharEKyc.AadharData.PIN_CODE));
                if (pinCode != null) {
                    user.setPinCodeId(pinCode.getId());
                }
                user.save();
            }
        }
        return IntegrationAdaptor.instance(User.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).createResponse(getPath(),
                user,user.getReflector().getFields(),new HashSet<>(),getIncludedModelFields());
    }

    @Override
    protected String[] getIncludedFields() {
        return getIncludedModelFields().get(User.class).toArray(new String[]{});
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>,List<String>> map = super.getIncludedModelFields();
        if (!map.containsKey(SignUp.class)) {
            map.put(SignUp.class, ModelReflector.instance(SignUp.class).getVisibleFields());
        }
        if (!map.containsKey(UserRole.class)) {
            map.put(UserRole.class, Arrays.asList("ID", "USER_ID", "ROLE_ID"));
        }
        if (!map.containsKey(Role.class)) {
            map.put(Role.class, Arrays.asList("ID", "NAME"));
        }
        if (!map.containsKey(Device.class)) {
            map.put(Device.class, ModelReflector.instance(Device.class).getVisibleFields());
        }
        if (!map.containsKey(SavedAddress.class)) {
            map.put(SavedAddress.class, ModelReflector.instance(SavedAddress.class).getVisibleFields());
        }
        if (!map.containsKey(User.class)) {
            map.put(User.class, ModelReflector.instance(User.class).getVisibleFields());
        }
        return map;
    }

    @Override
    public String getTemplateDirectory() {
        return getTemplateDirectory(getReflector().getTableName().toLowerCase());
    }


    @Override
    public View index() {
        if (getReturnIntegrationAdaptor() != null){
            return super.index();
        }else {
            if (TemplateEngine.getInstance(getTemplateDirectory()).exists("/html/index.html")){
                return html("index");
            }else {
                return super.index();
            }
        }

    }

    @Override
    public View show(long id) {
        if (getReturnIntegrationAdaptor() != null){
            return super.show(id);
        }else {
            if (TemplateEngine.getInstance(getTemplateDirectory()).exists("/html/show.html")){
                return redirectTo("html/show?id="+id);
            }else {
                return super.show(id);
            }
        }

    }

    @Override
    public View edit(long id) {
        if (TemplateEngine.getInstance(getTemplateDirectory()).exists("/html/edit.html")){
            return redirectTo("html/edit?id="+id);
        }else {
            return super.edit(id);
        }

    }

    @Override
    public View blank() {
        if (getReturnIntegrationAdaptor() != null){
            return super.blank();
        }else {
            if (TemplateEngine.getInstance(getTemplateDirectory()).exists("/html/blank.html")){
                return redirectTo("html/blank");
            }else {
                return super.blank();
            }
        }
    }

    public View pendingKyc(){
        if (TemplateEngine.getInstance(getTemplateDirectory()).exists("/html/pendingKyc.html")){
            return redirectTo("html/pendingKyc");
        }else {
            getPath().addErrorMessage("Template missing " );
            return back();
        }
    }
    @RequireLogin(false)
    public View hasPassword() throws Exception {
        ensureIntegrationMethod(HttpMethod.POST);
        JSONObject input = (JSONObject)JSONValue.parse(StringUtil.read(getPath().getInputStream()));
        JSONObject local = _hasPassword(input);

        List<ServerNode> nodes = InternalNetwork.getNodes();
        Application application = getPath().getApplication();
        if (nodes.isEmpty() || (nodes.size() == 1 && nodes.get(0).isSelf()) || ObjectUtil.equals(local.get("PasswordSet"),"Y")){
            return new BytesView(getPath(),local.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
        }
        ServerNode caller = null ;
        ServerNode self = null;
        for (ServerNode node: nodes){
            if (node.isSelf()){
                self = node;
            }
            if (application != null && ObjectUtil.equals(application.getAppId(),node.getClientId())){
                caller = node;
            }
        }
        if (self == null){
            throw new AccessDeniedException("Cannot call api on this node");
        }
        if (caller != null) {
            return new BytesView(getPath(),local.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
        }

        Map<String,String> headers = new HashMap<>();
        if (self != null){
            String token = String.format("%s:%s",self.getClientId(),self.getClientSecret());
            token = String.format("Basic %s",Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
            headers.put("Authorization",token);
            headers.put("Content-Type",MimeType.APPLICATION_JSON.toString());
        }
        final TypeConverter<Boolean> booleanConverter = Database.getJdbcTypeHelper("").getTypeRef(Boolean.class).getTypeConverter();

        Comparator<JSONObject> comparator = (Comparator<JSONObject>) (r1, r2) -> {
            boolean registered1 = booleanConverter.valueOf(r1.get("Registered"));
            boolean registered2 = booleanConverter.valueOf(r2.get("Registered"));

            boolean passwordSet1 = booleanConverter.valueOf(r1.get("PasswordSet"));
            boolean passwordSet2 = booleanConverter.valueOf(r2.get("PasswordSet"));
            if (registered1 == registered2) {
                if (passwordSet1 == passwordSet2) {
                    return 0;
                }else if (passwordSet1){
                    return 1;
                }else {
                    return -1;
                }
            }else if (registered1) {
                return 1;
            }else {
                return -1;
            }
        };

        JSONObject best = local;
        for (ServerNode node: nodes){
            if (node.isSelf()){
                continue;
            }
            JSONObject aResponse = new Call<JSONObject>().url(node.getBaseUrl()+"/users/hasPassword").input(local).inputFormat(InputFormat.JSON).
                    headers(headers)
                    .method(HttpMethod.POST).getResponseAsJson();
            if (comparator.compare(aResponse,best) > 0){
                best = aResponse;
            }
            if (ObjectUtil.equals(best.get("PasswordSet"),"Y")){
                break;
            }
        }
        if (ObjectUtil.equals(best.get("Registered"),"N")){
            MobileMeta meta = MobileMeta.find((String)best.get("PhoneNumber"));
            if (meta != null){
                ServerNode node = meta.getServerNode();
                if (node != null){
                    best.put("BaseUrl",node.getBaseUrl());
                }
            }
        }
        return new BytesView(getPath(),best.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON);
    }

    @RequireLogin(false)
    public JSONObject _hasPassword(JSONObject object) throws IOException {
        object.put("BaseUrl",Config.instance().getServerBaseUrl());
        String phoneNumber = null;
        if (object != null){
            phoneNumber = Phone.sanitizePhoneNumber((String)object.get("PhoneNumber"));
        }

        boolean hasPassword = false;
        TypeConverter<Boolean> converter = getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter();

        com.venky.swf.db.model.User user = ObjectUtil.isVoid(phoneNumber) ? null : getPath().getUser("PHONE_NUMBER",phoneNumber);
        if (user != null){
            object.put("Registered","Y");
            hasPassword = user.getRawRecord().getAsProxy(User.class).isPasswordSet();
        }else {
            object.put("Registered","N");
        }

        object.put("PasswordSet",converter.toString(hasPassword));
        return object;
    }


}
