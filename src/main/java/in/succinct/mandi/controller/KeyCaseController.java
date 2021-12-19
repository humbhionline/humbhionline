package in.succinct.mandi.controller;

import com.venky.swf.controller.Controller;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.FormatHelper.KeyCase;
import com.venky.swf.path.Path;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;

public class KeyCaseController extends Controller {
    public KeyCaseController(Path path) {
        super(path);
    }

    public <T> View snake() throws  Exception{
        FormatHelper<T> helper = FormatHelper.instance(getPath().getProtocol(),getPath().getInputStream());
        helper.change_key_case(KeyCase.SNAKE);
        return new BytesView(getPath(),helper.toString().getBytes(),getPath().getReturnProtocol());
    }
    public <T> View camel() throws  Exception{
        FormatHelper<T> helper = FormatHelper.instance(getPath().getProtocol(),getPath().getInputStream());
        helper.change_key_case(KeyCase.CAMEL);
        return new BytesView(getPath(),helper.toString().getBytes(),getPath().getReturnProtocol());
    }
}
