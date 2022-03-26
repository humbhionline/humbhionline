package in.succinct.mandi.extensions;

import com.venky.cache.Cache;
import com.venky.core.util.ObjectHolder;
import com.venky.core.util.ObjectUtil;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.Controller.CacheOperation;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.path.Path;
import com.venky.swf.path._IPath;
import com.venky.swf.routing.Config;
import com.venky.swf.views.View;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public class URLCacheExtension implements Extension {
    private static URLCacheExtension urlCacheExtension = null;
    public static URLCacheExtension getInstance(){
        if (urlCacheExtension == null) {
            synchronized (URLCacheExtension.class) {
                if (urlCacheExtension == null) {
                    urlCacheExtension = new URLCacheExtension();
                }
            }
        }
        return urlCacheExtension;
    }
    static {
        Registry.instance().registerExtension(Controller.GET_CACHED_RESULT_EXTENSION,getInstance());
        Registry.instance().registerExtension(Controller.SET_CACHED_RESULT_EXTENSION,getInstance());
        Registry.instance().registerExtension(Controller.CLEAR_CACHED_RESULT_EXTENSION,getInstance());
    }
    private Map<String,Map<String,View>> cache = new Cache<String, Map<String, View>>(100,0.3) {
        @Override
        protected Map<String, View> getValue(String s) {
            return new Cache<String, View>(100,.3) {
                @Override
                protected View getValue(String s) {
                    return null;
                }
            };
        }
    };

    @Override
    public void invoke(Object... context) {
        _IPath path = (_IPath)context[1];
        ObjectHolder<View> holder = (ObjectHolder<View>)context[2];

        switch ((CacheOperation)context[0]){
            case GET:
                get(path,holder);
                break;
            case SET:
                put(path,holder);
                break;
            case CLEAR:
                cache.clear();
                break;
        }

    }
    /*public static void main(String [] args){
        System.out.println(getInstance().isCacheable("/services/destroy"));
    }*/

    Pattern[] cacheablePatterns = new Pattern[]{
            Pattern.compile("^("+ (Config.instance().isDevelopmentEnvironment()? "/resources/scripts/node_modules" : "" )+ ".*)\\.(jpg|jpeg|png|gif|ico|ttf|eot|svg|woff|woff2|css|js|map)$"),
    };
    private boolean isCacheable(String path){
        for (Pattern p : cacheablePatterns){
            if (p.matcher(path).matches()){
                return true;
            }
        }
        return false;
    }
    private String getURL(_IPath path) {
        StringBuilder requestURL = new StringBuilder(path.getTarget()); // Need to check based on target and not request path. Or else there is an infinite loop with forwarding.
        String queryString = path.getRequest().getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }

    private boolean isCacheable(_IPath path){
        if (!path.getRequest().getMethod().equalsIgnoreCase(HttpMethod.GET.toString())){
            return false;
        }

        return isCacheable(getURL(path));
    }
    public void get(_IPath path,ObjectHolder<View> holder){
        if (!isCacheable(path)){
            return;
        }
        View result = cache.get(getURL(path)).get(getReturnProtocol(path).toString());
        if (result != null){
            Config.instance().getLogger(getClass().getName()).info("{CachedUrl:"+ getURL(path) + ",  ContentType:" + getReturnProtocol(path) + "}");
        }
        holder.set(result);
    }
    public void put(_IPath path,ObjectHolder<View> holder){
        if (!isCacheable(path)){
            return;
        }
        cache.get(getURL(path)).put(getReturnProtocol(path).toString(),holder.get());
    }

    public MimeType getReturnProtocol(_IPath path){
        String apiprotocol = path.getRequest().getHeader("ApiProtocol"); // This is bc.
        if (ObjectUtil.isVoid(apiprotocol)){
            apiprotocol = path.getRequest().getHeader("accept");
            if (ObjectUtil.equals("*/*",apiprotocol)){
                apiprotocol = "";
            }
        }
        if (ObjectUtil.isVoid(apiprotocol)){
            return getProtocol(path);
        }
        return Path.getProtocol(apiprotocol);
    }

    public MimeType getProtocol(_IPath path){
        String apiprotocol = path.getRequest().getHeader("ApiProtocol"); // This is bc.
        if (ObjectUtil.isVoid(apiprotocol)) {
            apiprotocol = path.getRequest().getHeader("content-type");
        }
        return Path.getProtocol(apiprotocol);
    }

}
