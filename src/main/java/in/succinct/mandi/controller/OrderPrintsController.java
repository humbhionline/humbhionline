package in.succinct.mandi.controller;

import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.path.Path;
import com.venky.swf.views.View;
import in.succinct.plugins.ecommerce.db.model.order.OrderPrint;

public class OrderPrintsController extends ModelController<OrderPrint> {
    public OrderPrintsController(Path path) {
        super(path);
    }

    @Override
    @RequireLogin(false)
    public View view(long id) {
        return super.view(id);
    }
}
