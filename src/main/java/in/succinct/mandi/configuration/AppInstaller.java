package in.succinct.mandi.configuration;

import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.mandi.db.model.EncryptedModel;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.User;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;

import java.util.List;

public class AppInstaller implements Installer {

    public void install() {
        Database.getInstance().resetIdGeneration();
        encryptAddress(Facility.class);
        encryptAddress(User.class);
        encryptAddress(OrderAddress.class);
    }

    public <M extends Model & Address> void encryptAddress(Class<M> modelClass) {
        List<EncryptedModel> statuses = new Select().from(EncryptedModel.class).where(
                new Expression(
                        ModelReflector.instance(EncryptedModel.class).getPool(),
                        "NAME", Operator.EQ, modelClass.getSimpleName())).execute();
        if (statuses.isEmpty()) {
            List<M> addresses = new Select().from(modelClass).execute(modelClass,false);
            List<String> fields = ModelReflector.instance(modelClass).getEncryptedFields();

            addresses.forEach(a -> {
                for (String f : fields) {
                    a.getRawRecord().markDirty(f);
                }
                a.getRawRecord().markDirty("LAT");
                a.getRawRecord().markDirty("LNG");
                a.save(false); //Avoid going to update lat and lng.
            });

            EncryptedModel encryptedModel = Database.getTable(EncryptedModel.class).newRecord();
            encryptedModel.setName(modelClass.getSimpleName());
            encryptedModel.save();
        }

    }
}

