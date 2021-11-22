package in.succinct.mandi.db.model;


import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.model.Model;
import in.succinct.plugins.ecommerce.db.model.order.PersonAddress;

@IS_VIRTUAL
public interface DeliveryAddress extends PersonAddress, Model {

}
