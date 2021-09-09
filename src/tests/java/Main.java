import com.venky.core.math.DoubleHolder;
import com.venky.core.security.Crypt;
import in.succinct.beckn.Request;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
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
    public void testFunc(){
        Object o1 = Long.valueOf(1L);
        Object o2 = Long.valueOf(2L);
        Assert.assertEquals("Long Version Called!",func(o1,o2));
    }
    public String func(Object o1, Object o2){
        return ("Object Version Called!");
    }
    public String func(Long o1, Long o2){
        return ("Long Version Called!");
    }
    @Test
    public void bitTest(){
        long x = 0x7ffffL;
        long y = 0xfffffffffffL;
        long MASK = x << 44;

        long nodeId = 1;
        long id = 15;

        long fullId = nodeId << 44|id ;


        System.out.println(Long.toHexString((1L << 44)|id));
        System.out.println(Long.toHexString(nodeId << 44|id));
        System.out.println(Long.toHexString(fullId >> 44));
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
    @org.junit.BeforeClass
    public static void setup(){
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null){
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void encryptinTest2() throws Exception {


        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519","SunEC");
        KeyPair kp1 = kpg.generateKeyPair();
        KeyPair kp2 = kpg.generateKeyPair();

        String p1Pub = Crypt.getInstance().getBase64Encoded(kp1.getPublic());
        String p2Priv = Crypt.getInstance().getBase64Encoded(kp2.getPrivate());

        byte [] binCpk = Base64.getDecoder().decode(p1Pub);
        X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(binCpk);


        KeyFactory keyFactory = KeyFactory.getInstance("X25519","SunEC");
        PublicKey p1PubKey = keyFactory.generatePublic(pkSpec);

        binCpk = Base64.getDecoder().decode(p2Priv);
        PKCS8EncodedKeySpec pvkSpec = new PKCS8EncodedKeySpec(binCpk);

        PrivateKey p2PrivKey = keyFactory.generatePrivate(pvkSpec);

        KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
        keyAgreement.init(p2PrivKey);
        keyAgreement.doPhase(p1PubKey,true);
        SecretKey key = keyAgreement.generateSecret("TlsPremasterSecret");



    }
    @Test
    public void encryptionTest1() throws Exception {
        KeyPair kp1 = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair kp2 = KeyPairGenerator.getInstance("X25519").generateKeyPair();

        KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
        keyAgreement.init(kp1.getPrivate());
        keyAgreement.doPhase(kp2.getPublic(),true);

        KeyAgreement keyAgreement2 = KeyAgreement.getInstance("X25519");
        keyAgreement2.init(kp2.getPrivate());
        keyAgreement2.doPhase(kp1.getPublic(),true);


        SecretKey key1 = keyAgreement.generateSecret("TlsPremasterSecret");
        SecretKey key2 = keyAgreement2.generateSecret("TlsPremasterSecret");

        Assert.assertArrayEquals(key1.getEncoded(),key2.getEncoded());

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE,key1);
        byte[] encryped  = cipher.doFinal("Venky".getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.DECRYPT_MODE,key2);
        byte[] decrypted = cipher.doFinal(encryped);
        System.out.println(new String(decrypted));
    }

    /*
    @Test
    public void encryptionTest() throws Exception{
        ECNamedCurveParameterSpec paramSpec = ECNamedCurveTable.getParameterSpec("Curve25519");

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDH");
        keyPairGenerator.initialize(paramSpec);
        KeyPair kp1 = keyPairGenerator.generateKeyPair();
        KeyPair kp2 = keyPairGenerator.generateKeyPair();

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(kp1.getPrivate());
        agreement.doPhase(kp2.getPublic(),true);


        SecretKey key = agreement.generateSecret("AES[128]");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] encryped  = cipher.doFinal("Venky".getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.DECRYPT_MODE,key);
        byte[] decrypted = cipher.doFinal(encryped);
        System.out.println(new String(decrypted));



    }*/
}
