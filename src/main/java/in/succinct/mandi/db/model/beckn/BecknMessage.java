package in.succinct.mandi.db.model.beckn;

import com.venky.core.util.Bucket;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.model.Model;

import java.io.Reader;
import java.util.List;

public interface BecknMessage extends Model {
    @UNIQUE_KEY
    public String getMessageId();
    public void setMessageId(String messageId);

    public String getCallBackUri();
    void setCallBackUri(String callBackUri);

    @COLUMN_SIZE(4098)
    public String getRequestPayload();
    public void setRequestPayload(String payload);

    public long getExpirationTime();
    public void setExpirationTime(long l);



    public Reader getResponse();
    public void setResponse(Reader response);


    public Bucket getNumPendingResponses();
    public void setNumPendingResponses(Bucket pendingResponses);

    public List<ServerResponse> getServerResponses();

}
