package in.succinct.mandi.agents.beckn;

import com.venky.swf.sql.Select;
import in.succinct.beckn.CancellationReasons;
import in.succinct.beckn.Descriptor;

import in.succinct.beckn.Option;
import in.succinct.beckn.Options;
import in.succinct.beckn.Request;
import in.succinct.mandi.db.model.OrderCancellationReason;
import in.succinct.mandi.util.beckn.BecknUtil;
import in.succinct.mandi.util.beckn.BecknUtil.Entity;

import java.util.List;
import java.util.Map;

public class CancellationReason extends BecknAsyncTask {
    public CancellationReason(Request request, Map<String, String> headers) {
        super(request, headers);
    }

    @Override
    public Request executeInternal() {
        Request callback = new Request();
        callback.setContext(getRequest().getContext());
        callback.getContext().setAction("cancellation_reasons");

        CancellationReasons options = new CancellationReasons();

        List<OrderCancellationReason> reasons = new Select().from(OrderCancellationReason.class).execute();
        reasons.forEach(r->{
            if (r.isUsableBeforeDelivery()){
                Option option = new Option();
                option.setId(BecknUtil.getBecknId(String.valueOf(r.getId()),
                        Entity.cancellation_reason));
                Descriptor descriptor = new Descriptor();
                option.setDescriptor(descriptor);
                descriptor.setName(r.getReason());
                descriptor.setCode(String.valueOf(r.getId()));
                options.add(option);
            }
        });
        callback.setCancellationReasons(options);

        return callback;
    }
}
