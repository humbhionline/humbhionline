package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.relationship.CONNECTED_VIA;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import in.succinct.plugins.ecommerce.db.model.order.OrderStatus;

import java.util.List;

@IS_VIRTUAL
public interface RefOrder extends Order{
    @CONNECTED_VIA("ORDER_ID")
    public List<OrderStatus> getOrderStatuses();

    @CONNECTED_VIA("ORDER_ID")
    public List<OrderAddress> getAddresses();


    @CONNECTED_VIA("ORDER_ID")
    public List<OrderLine> getOrderLines();

}
