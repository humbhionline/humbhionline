package in.succinct.mandi.controller;

import com.venky.swf.controller.VirtualModelController;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.extensions.beforesave.BeforeSaveAddress.LocationSetterTask;
import com.venky.swf.plugins.collab.extensions.beforesave.BeforeValidateAddress;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.DeliveryAddress;

import java.util.List;

public class DeliveryAddressesController extends VirtualModelController<DeliveryAddress> {
    public DeliveryAddressesController(Path path) {
        super(path);
    }
    public View locate(){
        List<DeliveryAddress> addressList = getIntegrationAdaptor().readRequest(getPath());

        for (DeliveryAddress address : addressList){
            new BeforeValidateAddress<DeliveryAddress>().beforeValidate(address);
            new LocationSetterTask<>(address).setLatLng(false);
        }
        return getReturnIntegrationAdaptor().createResponse(getPath(),addressList);
    }

}
