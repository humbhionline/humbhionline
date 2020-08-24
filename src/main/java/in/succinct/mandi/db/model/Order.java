package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import in.succinct.plugins.ecommerce.db.model.participation.PreferredCarrier;

public interface Order extends in.succinct.plugins.ecommerce.db.model.order.Order {

    @COLUMN_DEF(value = StandardDefault.SOME_VALUE, args = PreferredCarrier.HAND_DELIVERY)
    public String getPreferredCarrierName();

    @PARTICIPANT
    public Long getFacilityId();
    public void setFacilityId(Long facilityId);
    public Facility getFacility();



    @Override
    @PARTICIPANT
    Long getCreatorUserId();


    void completePayment();
    void completeRefund();


    @COLUMN_DEF(StandardDefault.ZERO)
    @HIDDEN
    double getAmountPaid();
    void setAmountPaid(double amountPaid);

    @COLUMN_DEF(StandardDefault.ZERO)
    @HIDDEN
    double getAmountRefunded();
    void setAmountRefunded(double amountRefunded);

    @IS_VIRTUAL
    double getAmountPendingPayment();

    @IS_VIRTUAL
    double getAmountToRefund();



    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isPaymentInitialized();
    void setPaymentInitialized(boolean initialized);

    public void initializePayment();
    public void resetPayment();

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isRefundInitialized();
    void setRefundInitialized(boolean initialized);

    public void initializeRefund();
    public void resetRefund();




}
