import com.venky.core.math.DoubleHolder;
import org.junit.Assert;
import org.junit.Test;
import org.owasp.encoder.Encode;
import org.owasp.encoder.Encoder;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Objects;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args){

        for (Double value : new Double[]{1.001, 1.01, 1.09, 1.1,1.12233,1.4,1.45,1.55, 1.5, 1.99 , 1.9 ,1.91 , 1.98}){
            if (value - Math.floor(value) < 0.1 || Math.ceil(value) - value <0.1){
                System.out.println(value + " rounded to " + (double)Math.round(value));
                continue;
            }
            System.out.println(value + " rounded to " + new DoubleHolder(value,2).getHeldDouble().doubleValue());
        }


    }

    @Test
    public void test() throws Exception{
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/index"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/dashboard"));
        Assert.assertTrue(!isUrlFromOurSite("https://humbhionline.in/dashboard?hello=true"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/dashboard?search=HBO Subscription"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/catalog/show/Venky.home"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/orders?type=sales"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/orders?type=purchases"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/orders?type=sales&history=Y"));
        Assert.assertFalse(isUrlFromOurSite("https://humbhionline.in/orders?type=sales&history=N"));
        Assert.assertFalse(isUrlFromOurSite("https://humbhionline.in/orders?type=sales&history=N&history=Y"));

        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/orders?type=purchases&history=Y"));
        Assert.assertFalse(isUrlFromOurSite("https://humbhionline.in/orders?type=purchases&history=N"));
        Assert.assertTrue(isUrlFromOurSite("https://humbhionline.in/dashboard?search=HBO%20Subscription"));
        Assert.assertTrue(DASHBOARD_PAGE_PATTERN.matcher(Constant.BASE_URL + "/dashboard").matches());
        Assert.assertTrue(DASHBOARD_PAGE_PATTERN.matcher(Constant.BASE_URL + "/dashboard/index").matches());

        Assert.assertTrue(isUrlFromOurSite(Constant.BASE_URL +"/orders?type=sales#Order-112"));
        Assert.assertTrue(OTP_PAGE_PATTERN.matcher(Constant.BASE_URL + "/login/index_otp?phone_number=+919845114551").matches());
        Assert.assertTrue(OTP_PAGE_PATTERN.matcher(Constant.BASE_URL + "/login/index_otp?phone_number=919845114551").matches());
        Assert.assertTrue(OTP_PAGE_PATTERN.matcher(Constant.BASE_URL + "/login/index_otp?phone_number=9845114551").matches());
        Assert.assertTrue(OTP_PAGE_PATTERN.matcher(Constant.BASE_URL + "/login/index_otp").matches());

        Assert.assertTrue(BASE_PATTERN.matcher(Constant.BASE_URL+"/x/y/19?x=a#1234").matches());
        Assert.assertNotEquals(Constant.BASE_URL,sanitize(Constant.BASE_URL+"/x/y/19?x=a#1234"));
        Assert.assertNotEquals(Constant.BASE_URL,sanitize(Constant.BASE_URL+"/x/y/19?x=a#12%2034"));
        Assert.assertNotEquals(Constant.BASE_URL,sanitize(Constant.BASE_URL+"/x/y/19?x=a#12%2034"));
        Assert.assertEquals(Constant.BASE_URL,sanitize(Constant.BASE_URL+"/x/y/19?x=a#12%5C%5C%4034"));
        Assert.assertEquals(Constant.BASE_URL,sanitize(Constant.BASE_URL+"/x/y/19?x=a#12\\@google.com"));




    }
    private static final Pattern BASE_PATTERN = Pattern.compile("^"+Constant.BASE_URL+"([A-z|0-9/?=# ]*)");
    private static Pattern DASHBOARD_PAGE_PATTERN = Pattern.compile("^" + Constant.BASE_URL+"/dashboard(/index)?$") ;
    private static Pattern OTP_PAGE_PATTERN = Pattern.compile("^" + Constant.BASE_URL+"/login/index_otp(\\?phone_number=[+]?[0-9]*)?$") ;

    private static final Pattern[] ourSitePatternsTriggeredExternally = new Pattern[] {
            Pattern.compile("^" + Constant.BASE_URL+"/orders\\?type=(sales|purchases)(#Order-[0-9]*)?(&history=Y)?$") ,
            Pattern.compile("^" + Constant.BASE_URL+"/dashboard\\?search=HBO Subscription$") ,
            Pattern.compile("^" + Constant.BASE_URL+"/catalog/show/([A-z]|[0-9]|[ \\-_.])*$"),
            Pattern.compile("^" + Constant.BASE_URL+"(/|/[A-z]*)?$"),
            OTP_PAGE_PATTERN,
    } ;

    static class Constant {
        static String HOST= "humbhionline.in";
        static String BASE_URL= "https://humbhionline.in";
    }
    public static boolean isUrlFromOurSite(String urlEncoded) throws Exception{
        if (urlEncoded == null){
            return false;
        }

        String url = URLDecoder.decode(urlEncoded,"UTF-8");
        for (Pattern pattern : ourSitePatternsTriggeredExternally){
            if (pattern.matcher(url).matches()){
                return true;
            }
        }
        return false;
    }

    public String sanitize(String url){
        String ret = Constant.BASE_URL;
        try {

            if (BASE_PATTERN.matcher(URLDecoder.decode(url,"UTF-8")).matches()){
                URI uri = new URL(url).toURI();
                if (Objects.equals("https",uri.getScheme()) && Objects.equals(Constant.HOST,uri.getHost()) &&
                    Objects.equals(Constant.HOST,uri.getAuthority())){
                    ret = url;
                }
            }
        }catch (Exception ex){
            //
        }
        return ret;
    }

}
