package in.succinct.mandi.extensions;

import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import in.succinct.mandi.db.model.Item;
import in.succinct.mandi.util.CompanyUtil;

public class BeforeValidateItem  extends BeforeModelValidateExtension<Item> {
    static {
        registerExtension(new BeforeValidateItem());
    }
    @Override
    public void beforeValidate(Item model) {
        if (model.getCompanyId() == null){
            model.setCompanyId(CompanyUtil.getCompanyId());
        }
    }
}
