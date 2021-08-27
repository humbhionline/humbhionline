package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;
import in.succinct.mandi.db.model.ServerNode;

import java.io.Reader;

public interface ServerResponse extends Model {
    @UNIQUE_KEY
    public Long getBecknMessageId();
    public void setBecknMessageId(Long id);
    public BecknMessage getBecknMessage();

    @UNIQUE_KEY
    public Long getServerNodeId();
    public void setServerNodeId(Long id);
    public ServerNode getServerNode();


    public Reader getResponse();
    public void setResponse(Reader response);

}
