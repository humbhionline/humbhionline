package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.exceptions.AccessDeniedException;
import com.venky.swf.path.Path;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.beckn.Acknowledgement;
import in.succinct.beckn.Acknowledgement.Status;
import in.succinct.beckn.Request;
import in.succinct.beckn.Response;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.db.model.beckn.BecknMessage;
import in.succinct.mandi.db.model.beckn.BecknMessage.CancelConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.ConfirmConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.InitConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.SelectConsolidator;
import in.succinct.mandi.db.model.beckn.BecknMessage.StatusConsolidator;
import in.succinct.mandi.db.model.beckn.ServerResponse;
import in.succinct.mandi.util.InternalNetwork;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class BecknMessagesController extends ModelController<BecknMessage> {
    public BecknMessagesController(Path path) {
        super(path);
    }

    private ServerNode getNode(){
        ServerNode fromNode = InternalNetwork.getRemoteServer(getPath(),true);

        if (fromNode == null){
            throw new AccessDeniedException("AppId does not belong to a approved ServerNode");
        }
        return fromNode;
    }
    public View update_response() {
        try {
            ServerNode fromNode = getNode();

            Request request = new Request(StringUtil.read(getPath().getInputStream()));
            List<BecknMessage> messages = new Select(true,true).from(BecknMessage.class).where(
                    new Expression(ModelReflector.instance(BecknMessage.class).getPool(),"MESSAGE_ID",Operator.EQ,request.getContext().getMessageId())).execute(1);
            if (messages.size() != 1){
                throw new RuntimeException("Unknown messageId");
            }
            BecknMessage message = messages.get(0);

            ServerResponse response = Database.getTable(ServerResponse.class).newRecord();
            response.setBecknMessageId(message.getId());
            response.setServerNodeId(fromNode.getId());
            response = Database.getTable(ServerResponse.class).getRefreshed(response);
            if (response.getRawRecord().isNewRecord()) {
                throw new RuntimeException("Unknown ServerNodeId");
            }
            response.setResponse(new InputStreamReader(getPath().getInputStream()));
            response.save();

            message.getNumPendingResponses().decrement();
            message.save();

            return ack(request);
        }catch (IOException ex){
            throw new RuntimeException(ex);
        }
    }
    public View ack(Request request){
        Acknowledgement ack = new Acknowledgement(Status.ACK);
        return new BytesView(getPath(),new Response(request.getContext(),ack).toString().getBytes(StandardCharsets.UTF_8));
    }

    @RequireLogin(false)
    public View on_search() {
        return update_response();
    }

    @RequireLogin(false)
    public View on_select(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_init(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_confirm(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_status(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_cancel(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_update(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_track(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_rating(){
        return update_response();
    }

    @RequireLogin(false)
    public View on_support(){
        return update_response();
    }


}
