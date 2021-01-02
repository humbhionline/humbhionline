package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.PinCode;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.plugins.collab.db.model.user.Phone;
import com.venky.swf.plugins.collab.db.model.user.UserEmail;
import com.venky.swf.plugins.collab.db.model.user.UserPhone;
import com.venky.swf.plugins.mobilesignup.db.model.SignUp;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.plugins.templates.util.templates.TemplateEngine;
import com.venky.swf.routing.Config;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.AadharEKyc;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class UsersController extends com.venky.swf.plugins.collab.controller.UsersController implements TemplateLoader {
    public UsersController(Path path) {
        super(path);
    }

    @RequireLogin
    public View current() {
        return show(getSessionUser());
    }

    @Override
    @RequireLogin
    public HtmlView html(String path) {
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
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);

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
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>,List<String>> map = super.getIncludedModelFields();
        map.put(SignUp.class, ModelReflector.instance(SignUp.class).getVisibleFields());
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

    @RequireLogin(false)
    public View hasPassword() throws IOException {
        ensureIntegrationMethod(HttpMethod.POST);
        JSONObject object = (JSONObject)JSONValue.parse(StringUtil.read(getPath().getInputStream()));
        String phoneNumber = null;
        if (object != null){
            phoneNumber = Phone.sanitizePhoneNumber((String)object.get("PhoneNumber"));
        }
        com.venky.swf.db.model.User user = getPath().getUser("PHONE_NUMBER",phoneNumber);
        boolean hasPassword = false;
        if (user != null){
            hasPassword = user.getRawRecord().getAsProxy(User.class).isPasswordSet();
        }
        object.put("PasswordSet",getReflector().getJdbcTypeHelper().getTypeRef(boolean.class).getTypeConverter().toString(hasPassword));
        return new BytesView(getPath(),object.toJSONString().getBytes(StandardCharsets.UTF_8));
    }
}
