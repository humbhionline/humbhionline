package in.succinct.mandi.configuration;

import com.venky.core.security.Crypt;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.digest.Encryptor;
import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.jdbc.ConnectionManager;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.beckn.messaging.BapSubscriber;
import com.venky.swf.plugins.beckn.messaging.BppSubscriber;
import com.venky.swf.plugins.beckn.messaging.QueueSubscriber;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.plugins.collab.db.model.CryptoKey;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.plugins.collab.db.model.participants.admin.Company;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.plugins.sequence.db.model.SequentialNumber;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.beckn.BecknObject;
import in.succinct.beckn.Request;
import in.succinct.mandi.agents.beckn.Cancel;
import in.succinct.mandi.agents.beckn.Confirm;
import in.succinct.mandi.agents.beckn.Init;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.agents.beckn.Status;
import in.succinct.mandi.agents.beckn.Track;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.agents.beckn.bap.delivery.GenericCallBackRecorder;
import in.succinct.mandi.agents.beckn.bap.delivery.OnStatus;
import in.succinct.mandi.controller.ServerNodesController.Sync;
import in.succinct.mandi.db.model.EncryptedModel;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.SavedAddress;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.extensions.AfterSaveOrderAddress;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class AppInstaller implements Installer {

    public void install() {
        installIndexes();
        boolean encryptionSupport = (Config.instance().getBooleanProperty("swf.encryption.support",true));
        encryptAddress(Facility.class,encryptionSupport);
        encryptAddress(User.class,encryptionSupport);
        encryptAddress(OrderAddress.class,encryptionSupport);
        encryptAddress(SavedAddress.class,encryptionSupport);
        createSavedAddresses();
        insertCompany();
        insertRole();

        //installGuestUser();
        generateBecknKeys();
        registerBecknKeys();
        updateFacilityMinMaxLatLng();

    }
    public void createSavedAddresses(){
        List<SavedAddress> addresses = new Select().from(SavedAddress.class).execute(1);
        if (!addresses.isEmpty()){
            return;
        }
        ModelReflector<OrderAddress> ref = ModelReflector.instance(OrderAddress.class);

        List<OrderAddress> orderAddresses = new Select().from(OrderAddress.class).where(new Expression(ref.getPool(),"ADDRESS_TYPE",Operator.EQ,OrderAddress.ADDRESS_TYPE_SHIP_TO)).execute();
        AfterSaveOrderAddress afterSaveOrderAddress = new AfterSaveOrderAddress();
        for (OrderAddress address : orderAddresses) {
            afterSaveOrderAddress.afterSave(address);
        }
    }
    private void installIndexes()  {
        for (String pool : ConnectionManager.instance().getPools()){
            StringBuilder path = new StringBuilder();
            path.append("database/indexes");
            if (!ObjectUtil.isVoid(pool)){
                path.append("/").append(pool);
            }
            File dir = new File(path.toString());
            if (!dir.exists()){
                continue;
            }
            for (File file : Objects.requireNonNull(dir.listFiles((dir1, name) -> name.endsWith(".sql")))) {
                File doneFile = new File(file.getPath() +".done");
                if (doneFile.exists()) {
                    continue;
                }
                if (!file.exists()){
                    continue;
                }

                try{
                    InputStream inputStream = new FileInputStream(file);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    reader.lines().forEach(sql->{
                        try {
                            Config.instance().getLogger(getClass().getName()).log(Level.INFO,sql);
                            PreparedStatement statement = Database.getInstance().createStatement(pool, sql);
                            statement.execute();

                        }catch (Exception ex){
                            Config.instance().getLogger(getClass().getName()).log(Level.WARNING,sql ,ex);
                        }finally {
                            try {
                                FileUtils.touch(doneFile);
                            }catch (Exception ex){
                                Config.instance().getLogger(getClass().getName()).log(Level.WARNING,sql ,ex);
                            }
                        }
                    });
                }catch (Exception ex){
                    Config.instance().getLogger(getClass().getName()).log(Level.WARNING,ex.getMessage() ,ex);
                }
            }
        }
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
        Map<String,Map<String, Class<? extends Task>>> participantActions = new HashMap<String,Map<String, Class<? extends Task>>>(){{
            // put(NetworkRole.SUBSCRIBER_TYPE_BG,Arrays.asList("search")); No need for bg.!!
            put("nic2004:52110", new HashMap<String,Class<? extends Task>>(){{
                put("search", Search.class);
                put("select", in.succinct.mandi.agents.beckn.Select.class);
                put("init", Init.class);
                put("confirm", Confirm.class);
                put("track", Track.class);
                put("cancel", Cancel.class);
                put("update", Update.class);
                put("status", Status.class);
            }});
            put("nic2004:55204",new HashMap<String,Class<? extends Task>>(){{
                put("on_search", GenericCallBackRecorder.class);
                put("on_select", GenericCallBackRecorder.class);
                put("on_init", GenericCallBackRecorder.class);
                put("on_confirm", GenericCallBackRecorder.class);
                put("on_status", OnStatus.class);
                put("on_track", GenericCallBackRecorder.class);
                put("on_cancel", GenericCallBackRecorder.class);
                put("on_update", GenericCallBackRecorder.class);
            }});
        }};

        List<BecknNetwork> networks =  new Select().from(BecknNetwork.class).execute(BecknNetwork.class);
        for (BecknNetwork network : networks){
            if (network.isDisabled()){
                continue;
            }
            Map<String, Subscriber> subscriberMap = new HashMap<String,Subscriber>(){{
               put(BecknUtil.LOCAL_RETAIL,network.getRetailBppSubscriber());
               put(BecknUtil.LOCAL_DELIVERY,network.getDeliveryBapSubscriber());
            }};
            for (String subscriberPrefix : new String[]{ ".nic2004:55204.BAP" , ".nic2004:52110.BPP" }){
                String[] parts = subscriberPrefix.split("\\.");

                String domain  = parts[1];
                String type = parts[2];

                Subscriber subscriber = subscriberMap.get(domain);
                CryptoKey encryptionKey = CryptoKey.find(subscriber.getPubKeyId(),CryptoKey.PURPOSE_ENCRYPTION);
                CryptoKey key = CryptoKey.find(subscriber.getPubKeyId(),CryptoKey.PURPOSE_SIGNING);


                String uniqueKeyId = subscriber.getPubKeyId();
                String subscriberId = subscriber.getSubscriberId();
                if (ObjectUtil.isVoid( subscriberId)) {
                    continue;
                }

                if (network.isMqSupported()) {
                    QueueSubscriber queueSubscriber = null ;
                    if (ObjectUtil.equals(type, in.succinct.beckn.Subscriber.SUBSCRIBER_TYPE_BPP)) {
                        queueSubscriber = new BppSubscriber(subscriber);
                    }else {
                        queueSubscriber = new BapSubscriber(subscriber);
                    }
                    queueSubscriber.registerSubscriber();
                }

                if (!network.isSubscriptionActive()) {
                    JSONObject object = new JSONObject();
                    object.put("subscriber_id", subscriberId);
                    object.put("country", "IND");
                    object.put("city", "std:080");
                    object.put("nonce", Base64.getEncoder().encodeToString(String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)));
                    object.put("unique_key_id", uniqueKeyId);
                    object.put("signing_public_key", Request.getRawSigningKey(key.getPublicKey()));
                    object.put("encr_public_key", Request.getRawEncryptionKey(encryptionKey.getPublicKey()));
                    object.put("valid_from", BecknObject.TIMESTAMP_FORMAT.format(key.getUpdatedAt()));
                    object.put("valid_until", BecknObject.TIMESTAMP_FORMAT.format(
                            new Date(key.getUpdatedAt().getTime() + (long) (10L * 365.25D * 24L * 60L * 60L * 1000L)))); //10 years

                    Request request = new Request(object);

                    TaskManager.instance().executeAsync((Task) () -> {

                        JSONObject response = new Call<JSONObject>().url(network.getRegistryUrl() + "/subscribe").method(HttpMethod.POST).input(object).inputFormat(InputFormat.JSON).
                                header("Content-Type", MimeType.APPLICATION_JSON.toString()).
                                header("Accept", MimeType.APPLICATION_JSON.toString()).
                                header("Authorization", request.generateAuthorizationHeader(subscriberId, uniqueKeyId)).
                                getResponseAsJson();
                    }, false);
                }
            }


        }
        registerWithHumBhiOnlineRegistry();
    }

    private void registerWithHumBhiOnlineRegistry() {
        String hboRegistry = Config.instance().getProperty("hbo.registry.url");
        if (ObjectUtil.isVoid(hboRegistry)){
            return;
        }

        ServerNode node = Database.getTable(ServerNode.class).newRecord();
        node.setBaseUrl(Config.instance().getServerBaseUrl());
        node.setClientId(BecknUtil.getNetworkParticipantId());
        node = Database.getTable(ServerNode.class).getRefreshed(node);

        if (node.getRawRecord().isNewRecord()){
            if (!node.isRegistry()) {
                InputStream in = new Call<InputStream>().url(hboRegistry + "/server_nodes/next_node_id").getResponseStream();
                String next_node_id = StringUtil.read(in);
                node.setNodeId(Long.parseLong(next_node_id));
            }else  {
                node.setNodeId(1L);
                node.setApproved(true);
            }

            node.setClientSecret(Encryptor.encrypt(node.getClientId() + "-" + System.currentTimeMillis()));
            node.setSigningPublicKey(BecknUtil.getSelfKey(null,BecknUtil.LOCAL_RETAIL).getPublicKey());
            node.setEncryptionPublicKey(BecknUtil.getSelfEncryptionKey(null,BecknUtil.LOCAL_RETAIL).getPublicKey());
            node.save();
        }else {
            if (node.isRegistry()){
                node.setApproved(true);
                node.save();
            }else {
                TaskManager.instance().executeAsync(new Sync(), false);
            }
        }
        Config.instance().setProperty("swf.node.id",String.valueOf(node.getNodeId()));
        Database.getInstance().resetIdGeneration();

    }

    private void generateBecknKeys() {
        String skeyNumber = SequentialNumber.get("KEYS").getCurrentNumber();
        if (Long.parseLong(skeyNumber) == 0){
            skeyNumber = SequentialNumber.get("KEYS").next();
        }
        long keyNumber = Long.parseLong(skeyNumber);

        CryptoKey key = CryptoKey.find(BecknUtil.getCryptoKeyId(null),CryptoKey.PURPOSE_SIGNING);
        if (key.getRawRecord().isNewRecord()) {
            key.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            key.setCreatedAt(key.getUpdatedAt());
        }
        boolean keyExpired = key.getUpdatedAt().getTime() + (long)(10L * 365.25D * 24L * 60L * 60L * 1000L) <= System.currentTimeMillis();
        if ( key.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Request.SIGNATURE_ALGO,Request.SIGNATURE_ALGO_KEY_LENGTH);
            key.setAlgorithm(Request.SIGNATURE_ALGO);
            key.setPrivateKey(Crypt.getInstance().getBase64Encoded(pair.getPrivate()));
            key.setPublicKey(Crypt.getInstance().getBase64Encoded(pair.getPublic()));
            key.save();
        }

        CryptoKey encryptionKey =  CryptoKey.find(BecknUtil.getCryptoKeyId(null),CryptoKey.PURPOSE_ENCRYPTION);
        if (encryptionKey.getRawRecord().isNewRecord()) {
            encryptionKey.setUpdatedAt(key.getUpdatedAt());
            encryptionKey.setCreatedAt(key.getUpdatedAt());
        }
        if (encryptionKey.getRawRecord().isNewRecord() || keyExpired){
            KeyPair pair = Crypt.getInstance().generateKeyPair(Request.ENCRYPTION_ALGO,Request.ENCRYPTION_ALGO_KEY_LENGTH);
            encryptionKey.setAlgorithm(Request.ENCRYPTION_ALGO);
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

