package in.succinct.mandi.db.model.beckn;

import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.validations.Enumeration;
import com.venky.swf.db.model.Model;
import com.venky.swf.plugins.beckn.messaging.Subscriber;

public interface BecknNetworkRole extends Model {
    public Long getBecknNetworkId();
    public void setBecknNetworkId(Long id);
    public BecknNetwork getBecknNetwork();

    @IS_NULLABLE(false)
    public String getDomain();
    public void setDomain(String domain);

    @Enumeration(Subscriber.SUBSCRIBER_TYPE_BAP + "," + Subscriber.SUBSCRIBER_TYPE_BPP)
    public String getRole();
    public void setRole(String role);

    public String getDeliveryDomain();
    public void setDeliveryDomain(String deliveryDomain);

}
