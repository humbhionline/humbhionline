package in.succinct.mandi.extensions;

import com.venky.core.util.ObjectHolder;
import com.venky.extension.Extension;
import com.venky.extension.Registry;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.collab.db.model.Key;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.util.List;

public class BecknPrivateKeyFinder implements Extension {
    static {
        Registry.instance().registerExtension("beckn.private.key.get",new BecknPrivateKeyFinder());
    }

    @Override
    public void invoke(Object... context) {
        String subscriber_id = (String)context[0];
        String uniqueKeyId = (String)context[1];
        ObjectHolder<String> privateKeyHolder = (ObjectHolder<String>) context[2];
        List<Key> keys = new Select().from(Key.class).where(
                new Expression(ModelReflector.instance(Key.class).getPool(),"beckn_id", Operator.EQ,uniqueKeyId)).execute();
        if (keys.isEmpty()){
            throw new RuntimeException("Key :" + uniqueKeyId + " not generated! ");
        }
        privateKeyHolder.set(keys.get(0).getPrivateKey());
    }
}
