package in.succinct.mandi.controller;

import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.path.Path;
import com.venky.swf.controller.Controller;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import com.venky.swf.views.RedirectorView;
import com.venky.swf.views.View;
import in.succinct.mandi.db.model.Facility;
import in.succinct.mandi.util.CompanyUtil;

import java.util.List;

public class CatalogController extends Controller {
    public CatalogController(Path path) {
        super(path);
    }

    @RequireLogin(value = false)
    public View show(String name){
        ModelReflector<Facility> ref = ModelReflector.instance(Facility.class);
        Expression where = new Expression(ref.getPool(), Conjunction.AND);
        where.add(new Expression(ref.getPool(),"COMPANY_ID", Operator.EQ,CompanyUtil.getCompanyId()));
        where.add(new Expression(ref.getPool(), "NAME",Operator.EQ,name));
        List<Facility> list = new Select().from(Facility.class).where(where).execute();
        if (list.isEmpty()){
            return new RedirectorView(getPath(),"/dashboard","index");
        }else {
            return new RedirectorView(getPath(),"/dashboard", "index?facility_id="+list.get(0).getId());
        }

    }
}
