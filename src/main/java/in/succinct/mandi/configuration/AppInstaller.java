package in.succinct.mandi.configuration;

import com.venky.core.date.DateUtils;
import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.digest.Encryptor;
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
import com.venky.swf.plugins.collab.db.model.participants.admin.Company;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.EncryptedModel;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec;
import org.bouncycastle.jcajce.spec.XDHParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.simple.JSONObject;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.Security;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

public class AppInstaller implements Installer {

    public void install() {
        boolean encryptionSupport = (Config.instance().getBooleanProperty("swf.encryption.support",true));
        encryptAddress(Facility.class,encryptionSupport);
        encryptAddress(User.class,encryptionSupport);
        encryptAddress(OrderAddress.class,encryptionSupport);
        insertCompany();
        insertRole();

        //installGuestUser();
        generateBecknKeys();
        registerBecknKeys();
        updateFacilityMinMaxLatLng();

    }
    private void insertCompany(){
        Company company = Database.getTable(Company.class).newRecord();
        company.setName("MANDI");
        company = Database.getTable(Company.class).getRefreshed(company);
        company.save();
    }
    private void insertRole(){
        Role role = Database.getTable(Role.class).newRecord();
        role.setName("USER");
        role = Database.getTable(Role.class).getRefreshed(role);
        role.save();
    }

    private void updateFacilityMinMaxLatLng() {

        Select select = new Select().from(Facility.class);
        Expression where = new Expression(select.getPool(), Conjunction.AND);
        where.add(new Expression(select.getPool(),"DELIVERY_RADIUS" , Operator.GT , 0));
        where.add(new Expression(select.getPool(),"LAT" , Operator.NE ));
        where.add(new Expression(select.getPool(),"LNG" , Operator.NE ));
        Expression minMax = new Expression(select.getPool(),Conjunction.OR);
        where.add(minMax);
        minMax.add(new Expression(select.getPool(),"MIN_LAT" , Operator.EQ ));
        minMax.add(new Expression(select.getPool(),"MIN_LNG" , Operator.EQ ));
        minMax.add(new Expression(select.getPool(),"MAX_LAT" , Operator.EQ ));
        minMax.add(new Expression(select.getPool(),"MAX_LNG" , Operator.EQ ));

        List<Facility> facilities = select.where(where).execute();
        for (Facility f :facilities){
            f.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            f.save(); //Let before save do the trick.
        }

    }

    private void registerBecknKeys() {
        if (!ObjectUtil.isVoid(BecknUtil.getRegistryUrl())){
            CryptoKey encryptionKey = BecknUtil.getSelfEncryptionKey();
            CryptoKey key = BecknUtil.getSelfKey();
            String subscriberId = Config.instance().getHostName();
            String uniqueKeyId = key.getAlias();
            JSONObject object = new JSONObject();
            object.put("subscriber_id",subscriberId);
            object.put("country","IND");
            object.put("city","std:080");
            object.put("domain","nic2004:52110");
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
        registerWithHumBhiOnlineRegistry();
    }

    private void registerWithHumBhiOnlineRegistry() {
        String hboRegistry = Config.instance().getProperty("hbo.registry.url");
        if (ObjectUtil.isVoid(hboRegistry)){
            throw new RuntimeException("hbo.registry.url not set");
        }

        ServerNode node = Database.getTable(ServerNode.class).newRecord();
        node.setBaseUrl(Config.instance().getServerBaseUrl());
        node.setClientId(BecknUtil.getSubscriberId());
        node = Database.getTable(ServerNode.class).getRefreshed(node);

        if (node.getRawRecord().isNewRecord()){
            if (!node.isRegistry()) {
                InputStream in = new Call<InputStream>().url(hboRegistry + "/server_nodes/next_node_id").getResponseStream();
                String next_node_id = StringUtil.read(in);
                node.setNodeId(Long.parseLong(next_node_id));
            }else  {
                node.setNodeId(1L);
            }

            node.setClientSecret(Encryptor.encrypt(node.getClientId() + "-" + System.currentTimeMillis()));
            node.setSigningPublicKey(BecknUtil.getSelfKey().getPublicKey());
            node.setEncryptionPublicKey(BecknUtil.getSelfEncryptionKey().getPublicKey());
            node.save();
            Config.instance().setProperty("swf.node.id",String.valueOf(node.getNodeId()));
            Database.getInstance().resetIdGeneration();
        }

    }

    private void generateBecknKeys() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        CryptoKey key = Database.getTable(CryptoKey.class).newRecord();
        key.setAlias(Config.instance().getHostName() + ".k1");
        key = Database.getTable(CryptoKey.class).getRefreshed(key);
        if (key.getRawRecord().isNewRecord()) {
            key.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            key.setCreatedAt(key.getUpdatedAt());
        }
        boolean keyExpired = key.getUpdatedAt().getTime() + (long)(10L * 365.25D * 24L * 60L * 60L * 1000L) <= System.currentTimeMillis();
        if ( key.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Request.SIGNATURE_ALGO,Request.SIGNATURE_ALGO_KEY_LENGTH);
            key.setPrivateKey(Crypt.getInstance().getBase64Encoded(pair.getPrivate()));
            key.setPublicKey(Crypt.getInstance().getBase64Encoded(pair.getPublic()));
            key.save();
        }

        CryptoKey encryptionKey = Database.getTable(CryptoKey.class).newRecord();
        encryptionKey.setAlias(Config.instance().getHostName() + ".encrypt.k1");
        encryptionKey = Database.getTable(CryptoKey.class).getRefreshed(encryptionKey);

        if (encryptionKey.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Request.ENCRYPTION_ALGO,Request.ENCRYPTION_ALGO_KEY_LENGTH);
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

