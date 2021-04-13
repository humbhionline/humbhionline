package in.succinct.mandi.configuration;

import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.plugins.collab.db.model.Key;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.mandi.db.model.EncryptedModel;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public class AppInstaller implements Installer {

    public void install() {
        Database.getInstance().resetIdGeneration();
        boolean encryptionSupport = (Config.instance().getBooleanProperty("swf.encryption.support",true));
        encryptAddress(Facility.class,encryptionSupport);
        encryptAddress(User.class,encryptionSupport);
        encryptAddress(OrderAddress.class,encryptionSupport);
        //installGuestUser();
        generateBecknKeys();
    }

    private void generateBecknKeys() {
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

