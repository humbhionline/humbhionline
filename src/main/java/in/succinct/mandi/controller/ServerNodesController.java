package in.succinct.mandi.controller;

import com.venky.cache.Cache;
import com.venky.core.random.Randomizer;
import com.venky.core.security.Crypt;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.Database;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.Count;
import com.venky.swf.db.model.io.ModelIOFactory;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.integration.api.Call;
import com.venky.swf.integration.api.HttpMethod;
import com.venky.swf.integration.api.InputFormat;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.sql.Select;
import com.venky.swf.views.BytesView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.ServerNode;
import in.succinct.mandi.util.InternalNetwork;
import in.succinct.mandi.util.beckn.BecknUtil;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ServerNodesController extends ModelController<ServerNode> {
    public ServerNodesController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View next_node_id(){
        List<Count> counts = new Select("MAX(NODE_ID) AS ID", "COUNT(1) AS COUNT").from(getModelClass()).execute(Count.class);
        Long nextNodeId = 1L;
        if (!counts.isEmpty()){
            Count count = counts.get(0);
            nextNodeId = count.getId();
        }
        nextNodeId += 1;
        return new BytesView(getPath(),nextNodeId.toString().getBytes(StandardCharsets.UTF_8), MimeType.TEXT_PLAIN.toString());
    }

    @RequireLogin(false)
    public View register(){
        List<ServerNode> nodes = getIntegrationAdaptor().readRequest(getPath());
        if (nodes.isEmpty()){
            throw new RuntimeException("No Nodes to register!");
        }
        if (nodes.size() > 1){
            throw new RuntimeException("Only one node can be registered at a time!");
        }
        ServerNode node = nodes.get(0);
        node.setApproved(false);
        node.save();
        return challenge(node);
    }

    @RequireLogin(false)
    public View sync() throws Exception{
        if (getReturnIntegrationAdaptor() == null || !MimeType.APPLICATION_JSON.equals(getReturnIntegrationAdaptor().getMimeType())){
            throw new RuntimeException("Only json format supported");
        }

        if (InternalNetwork.isCurrentServerRegistry()){
            ServerNode remote = InternalNetwork.getRemoteServer(getPath());
            if (remote != null) {
                return getReturnIntegrationAdaptor().createResponse(getPath(),InternalNetwork.getNodes(),getReflector().getVisibleFields(new ArrayList<>()));
            }else {
                throw new RuntimeException("Must be called from peer node in humbhionline network");
            }
        }else {
            TaskManager.instance().executeAsync(new Sync(),false);
            return getIntegrationAdaptor().createStatusResponse(getPath(),null,"Being synchronized");
        }

    }

    public static class Sync implements Task{
        public Sync(){
        }

        @Override
        public void execute() {
            Cache<String,ServerNode> cache = new Cache<String, ServerNode>() {
                @Override
                protected ServerNode getValue(String clientId) {
                    ServerNode node = Database.getTable(ServerNode.class).newRecord();
                    node.setClientId(clientId);
                    return node;
                }
            };
            for (ServerNode node : new Select().from(ServerNode.class).execute(ServerNode.class)){
                cache.put(node.getClientId(),node);
            }

            Call<JSONObject> call = new Call<JSONObject>().url(InternalNetwork.getRegistryUrl() + "/server_nodes/sync")
                    .header("Authorization",ServerNode.selfNode().getAuthorizationHeader())
                    .header("Content-Type",MimeType.APPLICATION_JSON.toString());
            List<ServerNode> nodes = null;
            try {
                nodes = ModelIOFactory.getReader(ServerNode.class,
                        FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).read(call.getResponseStream());

                for (ServerNode node: nodes){
                    node.save();
                    cache.remove(node.getClientId());
                }

                for (String key : cache.keySet()){
                    ServerNode node = cache.get(key);
                    node.setApproved(false);
                    node.save();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public View challenge(long id){
        ServerNode node = Database.getTable(ServerNode.class).get(id);
        return challenge(node);
    }

    private View challenge(ServerNode node) {
        TaskManager.instance().executeAsync(new OnRegister(node),false);
        return IntegrationAdaptor.instance(getModelClass(),FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).createStatusResponse(getPath(),null, node.getClientId() + " is Being Approved.");
    }

    @RequireLogin(false)
    public View on_register() throws Exception{

        JSONObject object = (JSONObject) JSONValue.parse(new InputStreamReader(getPath().getInputStream()));
        String challenge = (String)object.get("challenge");

        String otherPublicKey = (String)object.get("pub_key");
        SecretKey key = getSecretKey(otherPublicKey);

        String decryptedChallenge = Crypt.getInstance().decrypt(challenge,"AES",key);
        object.put("challenge",decryptedChallenge);
        return new BytesView(getPath(),object.toString().getBytes(StandardCharsets.UTF_8),MimeType.APPLICATION_JSON.toString());
    }

    public static SecretKey getSecretKey(ServerNode otherNode){
        try {
            return getSecretKey(otherNode.getEncryptionPublicKey());
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }
    public static SecretKey getSecretKey(String b64StringX25519OtherPubKey){
        try {
            PublicKey otherKey = Crypt.getInstance().getPublicKey("X25519", b64StringX25519OtherPubKey);
            PrivateKey key = Crypt.getInstance().getPrivateKey("X25519", BecknUtil.getSelfEncryptionKey().getPrivateKey());
            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(key);
            agreement.doPhase(otherKey, true);
            SecretKey aesKey = agreement.generateSecret("TlsPremasterSecret");
            return aesKey;
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }

    }

    public static class OnRegister implements Task {
        ServerNode node;
        public OnRegister(ServerNode node){
            this.node = node;
        }


        @Override
        public void execute() {
            JSONObject on_register = new JSONObject();
            StringBuilder otp = new StringBuilder();
            for (int i = 0 ; i< 6 ; i ++){
                otp.append(Randomizer.getRandomNumber(0,9));
            }
            SecretKey secretKey = getSecretKey(node);

            on_register.put("pub_key", BecknUtil.getSelfEncryptionKey().getPublicKey());
            on_register.put("challenge", Crypt.getInstance().encrypt(otp.toString(),"AES",getSecretKey(node)));


            Call<JSONObject> onRegisterCall = new Call<JSONObject>().url(node.getBaseUrl() + "/server_nodes/on_register")
                    .header("content-type", MimeType.APPLICATION_JSON.toString())
                    .inputFormat(InputFormat.JSON).method(HttpMethod.POST)
                    .input(on_register);

            JSONObject response = onRegisterCall.getResponseAsJson();
            if (response != null){
                if (ObjectUtil.equals(response.get("challenge"),otp.toString())){
                    node.setApproved(true);
                    node.save();
                }
            }
        }
    }




}
