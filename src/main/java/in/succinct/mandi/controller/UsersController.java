package in.succinct.mandi.controller;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.db.model.config.City;
import com.venky.swf.plugins.collab.db.model.config.Country;
import com.venky.swf.plugins.collab.db.model.config.PinCode;
import com.venky.swf.plugins.collab.db.model.config.State;
import com.venky.swf.plugins.templates.controller.TemplateLoader;
import com.venky.swf.routing.Config;
import com.venky.swf.views.HtmlView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.AadharEKyc;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Date;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;

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

    @Override
    public String getTemplateDirectory() {
        StringBuilder dir = new StringBuilder();
        String templateDirectory = Config.instance().getProperty("swf.ftl.dir");
        if (!ObjectUtil.isVoid(templateDirectory)) {
            dir.append(templateDirectory);
        }
        dir.append("/users");
        return dir.toString();
    }

    public View doAadharKyc() throws Exception {
        HttpServletRequest request = getPath().getRequest();
        if (!request.getMethod().equalsIgnoreCase("POST")) {
            throw new RuntimeException("Cannot call save in any other method other than POST");
        }
        JSONObject formFields = (JSONObject) JSONValue.parse(new InputStreamReader(getPath().getInputStream()));
        User user = getPath().getSessionUser().getRawRecord().getAsProxy(User.class);

        if (!formFields.isEmpty()) {
            String sFileData = (String) formFields.get("zipfile");
            InputStream in = null;
            String expectedPrefix = "data:application/zip;base64,";
            if (sFileData.startsWith(expectedPrefix)) {
                in = new ByteArrayInputStream(Base64.getDecoder().decode(sFileData.substring(expectedPrefix.length())));
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
}
