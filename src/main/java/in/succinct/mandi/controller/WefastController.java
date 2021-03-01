package in.succinct.mandi.controller;

import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.templates.controller.TemplatedController;
import com.venky.swf.routing.Config;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.integrations.courier.Wefast;
import in.succinct.plugins.ecommerce.agents.order.tasks.deliver.DeliverOrderTask;
import in.succinct.plugins.ecommerce.db.model.attributes.AssetCode;
import in.succinct.plugins.ecommerce.db.model.order.OrderAttribute;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WefastController extends TemplatedController {
    public WefastController(Path path) {
        super(path);
    }


    public View record_status() throws Exception{
        String sign = getPath().getHeader("HTTP_X_DV_SIGNATURE");
        if (ObjectUtil.isVoid(sign)){
            throw new RuntimeException("No signature found!");
        }

        String payload = StringUtil.read(getPath().getInputStream());
        JSONObject input = (JSONObject) JSONValue.parse(payload);
        if (ObjectUtil.equals(input.get("event_type"),"order_changed")){
            Wefast wefast = new Wefast();

            JSONObject order = (JSONObject)input.get("order");

            long productOrderId = wefast.getOrderId(input);
            Order productOrder = Database.getTable(Order.class).get(productOrderId);
            if (productOrder != null){
                Order transportOrder = productOrder.getTransportOrder();

                String hash = createHMAC("HmacSHA1", getCallBackToken(transportOrder),payload);
                if (!ObjectUtil.equals(hash,sign)){
                    throw new RuntimeException("Signature does not match");
                }

                String trackingUrl = wefast.getTrackingUrl(input);
                if (!ObjectUtil.isVoid(trackingUrl)){
                    Map<String, OrderAttribute> map = transportOrder.getAttributeMap();
                    map.get("tracking_url").setValue(trackingUrl);
                    transportOrder.saveAttributeMap(map);
                }

                if (ObjectUtil.equals(order.get("status"),"completed")){
                    if (productOrder.getTransportOrder() != null){
                        TaskManager.instance().executeAsync(new DeliverOrderTask(productOrder.getTransportOrder().getId()));
                    }else {
                        TaskManager.instance().executeAsync(new DeliverOrderTask(Long.valueOf(productOrderId)));
                    }
                }

            }




        }


        return IntegrationAdaptor.instance(SWFHttpResponse.class, JSONObject.class).createStatusResponse(getPath(),null);
    }

    private String getCallBackToken(Order transportOrder) {
        List<OrderLine> lines = transportOrder.getOrderLines();
        List<Long> deliverySkuIds = AssetCode.getDeliverySkuIds();
        List<OrderLine> deliveryLines = lines.stream().filter(l-> deliverySkuIds.contains(l.getSkuId())).collect(Collectors.toList());
        if (deliveryLines.size() != 1){
            throw new RuntimeException("Don't know how to verify signature");
        }
        OrderLine deliveryLine = deliveryLines.get(0);
        return deliveryLine.getInventory().getRawRecord().getAsProxy(Inventory.class).getCallbackToken();

    }


    public String createHMAC(String algorithm, String secretKey, String message)
            throws NoSuchAlgorithmException, InvalidKeyException {

        // Create a key instance using the bytes of our secret key argument and
        // the proper algorithm
        SecretKey key = new SecretKeySpec(secretKey.getBytes(), algorithm);

        // Create a Mac instance using Bouncy Castle as the provider
        // and the specified algorithm
        Mac mac = Mac.getInstance(algorithm, new BouncyCastleProvider());

        // Initialize using the key and update with the data to
        // generate the mac from
        mac.init(key);
        mac.update(message.getBytes());

        // Perform the mac operation
        byte[] encrypted = mac.doFinal();

        StringWriter writer = new StringWriter();

        // Convert to hexadecimal representation
        for (byte b : encrypted) {
            writer.append(String.format("%02x", b));
        }

        return writer.toString();

    }
}
