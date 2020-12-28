package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelDestroyExtension;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.db.model.Order;
import in.succinct.mandi.db.model.User;

import java.util.List;

public class BeforeDestroyUser  extends BeforeModelDestroyExtension<User> {
    static {
        registerExtension(new BeforeDestroyUser());
    }
    @Override
    public void beforeDestroy(User model) {
        model.getSignUps().forEach(signUp -> signUp.destroy());
        List<Facility> facilityList =  new Select().from(Facility.class).where(new Expression(ModelReflector.instance(Facility.class).getPool(),"CREATOR_ID", Operator.EQ,model.getId())).execute();
        facilityList.forEach(f->f.destroy());
        List<Order> orders =  new Select().from(Order.class).where(new Expression(ModelReflector.instance(Order.class).getPool(),"CREATOR_ID", Operator.EQ,model.getId())).execute();
        orders.forEach(o->o.destroy());
    }
}
