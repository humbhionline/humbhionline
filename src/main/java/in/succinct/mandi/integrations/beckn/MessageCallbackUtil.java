package in.succinct.mandi.integrations.beckn;



import com.venky.swf.integration.api.Call;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class MessageCallbackUtil  {
    private MessageCallbackUtil(){

    }
    private static MessageCallbackUtil instance = new MessageCallbackUtil();
    public static MessageCallbackUtil getInstance(){
        return instance;
    }
    private Map<String,CallBackData> messageCallback = new HashMap<>();
    public void registerResponse(String messageId, JSONObject response){
        CallBackData data = messageCallback.get(messageId);
        if (data != null){
            data.add(response);
        }
    }
    public JSONObject getNextResponse(String msgId, long timeOutMillis){
        CallBackData data = messageCallback.get(msgId);
        if (data == null){
            data = new CallBackData();
            messageCallback.put(msgId,data);
        }
        JSONObject nextResponse = data.readNextResponse(timeOutMillis);
        if (nextResponse == null){
            messageCallback.remove(msgId);
        }
        return null;
    }

    public static class CallBackData {
        private final LinkedList<JSONObject> responses = new LinkedList<>();
        public void add(JSONObject response) {
            synchronized (this){
                responses.add(response);
                this.notifyAll();
            }
        }


        public JSONObject readNextResponse(long timeOutMillis) {
            synchronized (this) {
                while (responses.isEmpty()) {
                    try {
                        this.wait(timeOutMillis);
                        break;
                    } catch (InterruptedException ex) {
                        //It was interrupted by some response being added.
                    }
                }
                if (!responses.isEmpty()) {
                    return responses.removeFirst();
                }
            }
            return null;
        }
    }
}
