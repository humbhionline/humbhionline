package in.succinct.mandi.configuration;

import com.venky.core.date.DateUtils;
import com.venky.core.security.Crypt;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.collab.db.model.CryptoKey;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.EncryptedModel;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.simple.JSONObject;

import java.security.KeyPair;
import java.security.Security;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppInstaller implements Installer {

    public void install() {
        Database.getInstance().resetIdGeneration();
        boolean encryptionSupport = (Config.instance().getBooleanProperty("swf.encryption.support",true));
        encryptAddress(Facility.class,encryptionSupport);
        encryptAddress(User.class,encryptionSupport);
        encryptAddress(OrderAddress.class,encryptionSupport);
        //installGuestUser();
        generateBecknKeys();
        registerBecknKeys();

    }

    private void registerBecknKeys() {
        if (!ObjectUtil.isVoid(BecknUtil.getRegistryUrl())){
            CryptoKey encryptionKey = BecknUtil.getSelfEncryptionKey();
            CryptoKey key = BecknUtil.getSelfKey();
            String subscriberId = Config.instance().getHostName();
            String uniqueKeyId = key.getAlias();
            JSONObject object = new JSONObject();
            object.put("subscriber_id",subscriberId);
            object.put("country","IN");
            object.put("city","080");
            object.put("domain","local-retail");
            object.put("type","bpp");
            object.put("signing_public_key",key.getPublicKey());
            object.put("encr_public_key",encryptionKey.getPublicKey());
            object.put("valid_from", DateUtils.getFormat(DateUtils.ISO_8601_24H_FULL_FORMAT).format(key.getUpdatedAt()));
            object.put("valid_until",DateUtils.getFormat(DateUtils.ISO_8601_24H_FULL_FORMAT).format(
                    new Date(key.getUpdatedAt().getTime() + (long)(10L * 365.25D * 24L * 60L * 60L * 1000L)))) ; //10 years
            object.put("subscriber_url",Config.instance().getServerBaseUrl() + "/bpp");

            try {
                JSONObject response = new Call<JSONObject>().url(BecknUtil.getRegistryUrl() + "/subscribe").method(HttpMethod.POST).input(object).inputFormat(InputFormat.JSON).
                        header("Content-Type", MimeType.APPLICATION_JSON.toString()).header("Authorization", new Request().generateAuthorizationHeader(subscriberId, uniqueKeyId)).getResponseAsJson();
            }catch (Exception ex){
                Config.instance().getLogger(getClass().getName()).log(Level.WARNING,ex.getMessage(),ex);
            }
        }
    }

    private void generateBecknKeys() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        /*
        Key key = Database.getTable(Key.class).newRecord();
        key.setAlias(BecknUtil.getDomainId() + ".k1");
        key = Database.getTable(Key.class).getRefreshed(key);
        if (key.getRawRecord().isNewRecord()){
            Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
            gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
            AsymmetricCipherKeyPair pair = gen.generateKeyPair();
            key.setPrivateKey(Base64.getEncoder().encodeToString(
                    ((Ed25519PrivateKeyParameters)pair.getPrivate()).getEncoded()));

            key.setPublicKey(Base64.getEncoder().encodeToString(
                    ((Ed25519PublicKeyParameters)pair.getPublic()).getEncoded()));
            key.save();
        }

        Key encryptionKey = Database.getTable(Key.class).newRecord();
        encryptionKey.setAlias(Config.instance().getHostName() + ".encrypt.k1");
        encryptionKey = Database.getTable(Key.class).getRefreshed(encryptionKey);
        if (encryptionKey.getRawRecord().isNewRecord()){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Crypt.KEY_ALGO);
            encryptionKey.setPrivateKey(Crypt.getInstance().getBase64Encoded(pair.getPrivate()));
            encryptionKey.setPublicKey(Crypt.getInstance().getBase64Encoded(pair.getPublic()));
            encryptionKey.save();
        }
        */

        CryptoKey key = Database.getTable(CryptoKey.class).newRecord();
        key.setAlias(Config.instance().getHostName() + ".k1");
        key = Database.getTable(CryptoKey.class).getRefreshed(key);
        if (key.getRawRecord().isNewRecord()) {
            key.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            key.setCreatedAt(key.getUpdatedAt());
        }
        boolean keyExpired = key.getUpdatedAt().getTime() + (long)(10L * 365.25D * 24L * 60L * 60L * 1000L) <= System.currentTimeMillis();
        if ( key.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(EdDSAParameterSpec.Ed25519,256);
            key.setPrivateKey(Crypt.getInstance().getBase64Encoded(pair.getPrivate()));
            key.setPublicKey(Crypt.getInstance().getBase64Encoded(pair.getPublic()));
            key.save();
        }

        CryptoKey encryptionKey = Database.getTable(CryptoKey.class).newRecord();
        encryptionKey.setAlias(Config.instance().getHostName() + ".encrypt.k1");
        encryptionKey = Database.getTable(CryptoKey.class).getRefreshed(encryptionKey);

        if (encryptionKey.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Crypt.KEY_ALGO,2048);
            encryptionKey.setPrivateKey(Crypt.getInstance().getBase64Encoded(pair.getPrivate()));
            encryptionKey.setPublicKey(Crypt.getInstance().getBase64Encoded(pair.getPublic()));
            encryptionKey.save();
        }

    }

    private void installGuestUser() {
        Table<com.venky.swf.db.model.User> USER = Database.getTable(com.venky.swf.db.model.User.class);

        Select q = new Select().from(com.venky.swf.db.model.User.class);
        ModelReflector<com.venky.swf.db.model.User> ref = ModelReflector.instance(com.venky.swf.db.model.User.class);
        String nameColumn = ref.getColumnDescriptor("name").getName();

        //This Encryption is the symmetic encryption using sharedkeys
        List<com.venky.swf.db.model.User> users = q.where(new Expression(ref.getPool(),nameColumn,Operator.EQ,new BindVariable(ref.getPool(),"guest"))).execute(com.venky.swf.db.model.User.class,false);

        if (users.isEmpty()){
            com.venky.swf.db.model.User u = USER.newRecord();
            u.setName("guest");
            u.setLongName("Guest");
            u.save();
        }

    }

    public <M extends Model & Address> void encryptAddress(Class<M> modelClass, boolean requiresEncryptionFinally) {
        List<EncryptedModel> statuses = new Select().from(EncryptedModel.class).where(
                new Expression(
                        ModelReflector.instance(EncryptedModel.class).getPool(),
                        "NAME", Operator.EQ, modelClass.getSimpleName())).execute();

        if (statuses.isEmpty() && !requiresEncryptionFinally){
            return;
        }else if (!statuses.isEmpty() && requiresEncryptionFinally){
            return;
        }else if (requiresEncryptionFinally){
            EncryptedModel encryptedModel = Database.getTable(EncryptedModel.class).newRecord();
            encryptedModel.setName(modelClass.getSimpleName());
            encryptedModel.save();
        }else {
            statuses.forEach(s->s.destroy());
        }
        if (ModelReflector.instance(modelClass).getEncryptedFields().isEmpty()){
            return;
        }


        SharedKeys.getInstance().setEnableEncryption(!requiresEncryptionFinally);
        List<M> addresses = new Select().from(modelClass).execute(modelClass);
        SharedKeys.getInstance().setEnableEncryption(requiresEncryptionFinally);
        reupdate(modelClass,addresses,requiresEncryptionFinally);
    }

    private <M extends Model & Address> void reupdate(Class<M> modelClass, List<M> addresses,boolean requiresEncryptionFinally) {
        List<String> fields = ModelReflector.instance(modelClass).getEncryptedFields();
        addresses.forEach(a -> {
            for (String f : fields) {
                a.getRawRecord().markDirty(f);
            }
            a.getRawRecord().markDirty("LAT");
            a.getRawRecord().markDirty("LNG");
            a.save(false); //Avoid going to update lat and lng.
        });

    }
}

