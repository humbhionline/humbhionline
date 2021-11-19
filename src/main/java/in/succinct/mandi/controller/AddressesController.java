package in.succinct.mandi.controller;

import com.venky.swf.controller.VirtualModelController;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.collab.extensions.beforesave.BeforeSaveAddress.LocationSetterTask;
import com.venky.swf.plugins.collab.extensions.beforesave.BeforeValidateAddress;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Address;

import java.util.List;

public class AddressesController extends VirtualModelController<Address> {
    public AddressesController(Path path) {
        super(path);
    }
    public View locate(){
        List<Address> addressList = getIntegrationAdaptor().readRequest(getPath());

        for (Address address : addressList){
            new BeforeValidateAddress<Address>().beforeValidate(address);
            new LocationSetterTask<>(address).setLatLng(false);
        }
        return getReturnIntegrationAdaptor().createResponse(getPath(),addressList);
    }

}
