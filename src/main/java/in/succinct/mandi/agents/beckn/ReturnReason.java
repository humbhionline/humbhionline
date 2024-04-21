package in.succinct.mandi.agents.beckn;

import in.succinct.beckn.Descriptor;
import in.succinct.beckn.Option;
import in.succinct.beckn.Request;
import in.succinct.beckn.ReturnReasons;
import in.succinct.beckn.ReturnReasons.ReturnReasonCode;

import java.util.Map;

public class ReturnReason extends BecknAsyncTask {
    public ReturnReason(Request request, Map<String, String> headers) {
        super(request, headers);
    }

    @Override
    public Request executeInternal() {
        Request callback = new Request();
        callback.setContext(getRequest().getContext());
        callback.getContext().setAction("cancellation_reasons");

        ReturnReasons options = new ReturnReasons();
        callback.setReturnReasons(options);


        for (ReturnReasonCode returnReasonCode : ReturnReasonCode.values()) {
            Option option = new Option();
            option.setId(ReturnReasonCode.convertor.toString(returnReasonCode));
            option.setDescriptor(new Descriptor());
            option.getDescriptor().setCode(ReturnReasonCode.convertor.toString(returnReasonCode));
            option.getDescriptor().setName(returnReasonCode.name());
            options.add(option);
        }

        return callback;
    }
}
