package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;

public interface EncryptedModel extends Model {
    @UNIQUE_KEY
    String getName();
    public void setName(String name);
}
