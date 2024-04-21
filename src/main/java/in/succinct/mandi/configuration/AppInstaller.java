package in.succinct.mandi.configuration;

import com.venky.core.security.Crypt;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.configuration.Installer;
import com.venky.swf.db.Database;
import com.venky.swf.db.jdbc.ConnectionManager;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.BindVariable;
import com.venky.swf.db.table.Table;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.beckn.messaging.BapSubscriber;
import com.venky.swf.plugins.beckn.messaging.BppSubscriber;
import com.venky.swf.plugins.beckn.messaging.QueueSubscriber;
import com.venky.swf.plugins.beckn.messaging.Subscriber;
import com.venky.swf.db.model.CryptoKey;
import com.venky.swf.plugins.collab.db.model.participants.admin.Company;
import com.venky.swf.plugins.security.db.model.Role;
import com.venky.swf.plugins.sequence.db.model.SequentialNumber;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.util.SharedKeys;
import in.succinct.beckn.Request;
import in.succinct.mandi.agents.beckn.Cancel;
import in.succinct.mandi.agents.beckn.Confirm;
import in.succinct.mandi.agents.beckn.Init;
import in.succinct.mandi.agents.beckn.Search;
import in.succinct.mandi.agents.beckn.Status;
import in.succinct.mandi.agents.beckn.Track;
import in.succinct.mandi.agents.beckn.Update;
import in.succinct.mandi.agents.beckn.bap.delivery.OnStatus;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.OrderAddress;
import in.succinct.mandi.db.model.SavedAddress;
import in.succinct.mandi.db.model.beckn.BecknNetwork;
import in.succinct.mandi.db.model.beckn.BecknNetworkRole;
import in.succinct.mandi.extensions.AfterSaveOrderAddress;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.onet.core.adaptor.NetworkAdaptorFactory;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class AppInstaller implements Installer {

    public void install() {
        Database.getInstance().resetIdGeneration();
        installIndexes();
        createSavedAddresses();
        insertCompany();
        insertRole();
        //registerBecknKeys();
        updateFacilityMinMaxLatLng();
        updateBecknNetwork();
    }
    private void updateBecknNetwork(){
        for (BecknNetwork network : BecknNetwork.all()) {
            if (ObjectUtil.isVoid(network.getSubscriberId())) {
                network.setSubscriberId(Config.instance().getHostName());
                network.save();
            }
        }
    }
    private <M extends Model> void fixOneTimeEncryptionMess(Class<M> modelClass){
        for (Model m : new Select().from(modelClass).execute()){
            try {
                for (String f : m.getReflector().getEncryptedFields()) {
                    m.getReflector().set(m, f, SharedKeys.getInstance().decrypt(m.getReflector().get(m, f)));
                }
                if (m.getReflector().getFields().contains("LAT")){
                    m.getRawRecord().markDirty("LAT");
                    m.getRawRecord().markDirty("LNG");
                }
                m.save(false);
            }catch (Exception ex){
                //
            }
        }
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

    /*
    private void registerBecknKeys() {
        List<BecknNetwork> networks =  new Select().from(BecknNetwork.class).execute(BecknNetwork.class);
        for (BecknNetwork network : networks){
            if (network.isDisabled()){
                continue;
            }
            if (!network.isSubscriptionActive()) {
                Subscriber bppSubscriber = network.getBppSubscriber();
                Subscriber bapSubscriber = network.getBapSubscriber();
                NetworkAdaptorFactory.getInstance().getAdaptor(network.getNetworkId()).subscribe(network.getBppSubscriber());
                NetworkAdaptorFactory.getInstance().getAdaptor(network.getNetworkId()).subscribe(network.getBapSubscriber());
                if (network.isMqSupported()){
                    new BppSubscriber(bppSubscriber).registerSubscriber();
                    new BapSubscriber(bapSubscriber).registerSubscriber();
                }
            }
        }
    }
    */


}

