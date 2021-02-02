package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.model.Model;

public interface Tag extends Model {
    @UNIQUE_KEY
    @Index
    public String getName();
    public void setName(String name);

}

