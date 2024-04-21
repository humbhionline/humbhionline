package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.CancellationReasons;
import in.succinct.beckn.CancellationReasons.CancellationReasonCode;
import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Option;
import in.succinct.beckn.Request;

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
        callback.setCancellationReasons(options);

        for (CancellationReasonCode cancellationReasonCode : CancellationReasonCode.values()) {
            Option option = new Option();
            option.setId(CancellationReasonCode.convertor.toString(cancellationReasonCode));
            option.setDescriptor(new Descriptor());
            option.getDescriptor().setCode(CancellationReasonCode.convertor.toString(cancellationReasonCode));
            option.getDescriptor().setName(cancellationReasonCode.name());
            options.add(option);
        }


        return callback;
    }
}
