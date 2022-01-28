package in.succinct.mandi.integrations.beckn;



import com.venky.swf.integration.api.Call;
import com.venky.swf.routing.Config;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;

public class MessageCallbackUtil  {
    private MessageCallbackUtil(){

    }
    private static MessageCallbackUtil instance = new MessageCallbackUtil();
    public static MessageCallbackUtil getInstance(){
        return instance;
    }
    private final Map<String,CallBackData> messageCallback = new Hashtable<>();
    public void registerResponse(String messageId, JSONObject response){
        CallBackData data = messageCallback.get(messageId);
        if (data != null){
            data.add(response);
        }else {
            Config.instance().getLogger(getClass().getName()).log(Level.WARNING,"Timeout Exceeded. Response thrashed!! :(");
        }
    }
    public void initializeCallBackData(String msgId){
        CallBackData data = messageCallback.get(msgId);
        if (data == null ) {
            data = new CallBackData();
            messageCallback.put(msgId, data);
        }
    }
    public void shutdownCallBacks(String msgId){
        messageCallback.remove(msgId);
    }
    public JSONObject getNextResponse(String msgId){
        CallBackData data = messageCallback.get(msgId);
        if (data != null){
            return data.readNextResponse();
        }
        return null;
    }

    public static class CallBackData {
        private final LinkedList<JSONObject> responses = new LinkedList<>();
        long timeOutMillis = (long)Math.pow(2,14); // 8 Seconds
        public void add(JSONObject response) {
            synchronized (this){
                responses.add(response);
                notifyAll();
            }
        }


        public JSONObject readNextResponse() {
            synchronized (this) {
                while (responses.isEmpty() && timeOutMillis >= 256) {
                    try {
                        wait(timeOutMillis);
                    } catch (InterruptedException ex) {
                        //It was interrupted by some response being added.
                    }finally {
                        timeOutMillis = timeOutMillis / 2L ;
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
