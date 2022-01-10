package in.succinct.mandi.db.model;

import com.venky.core.collections.SequenceSet;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.db.annotations.column.validations.Enumeration;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import in.succinct.plugins.ecommerce.db.model.catalog.UnitOfMeasure;
import in.succinct.plugins.ecommerce.db.model.inventory.Sku;
import in.succinct.plugins.ecommerce.db.model.order.OrderAddress;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;
import in.succinct.plugins.ecommerce.db.model.order.OrderStatus;
import in.succinct.plugins.ecommerce.db.model.participation.PreferredCarrier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Order extends in.succinct.plugins.ecommerce.db.model.order.Order {

    static Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>,List<String>> map = new HashMap<>();
        map.put(Order.class, ModelReflector.instance(Order.class).getVisibleFields(Arrays.asList("ID","LOCK_ID","CREATED_AT" ,"UPDATED_AT","CREATOR_USER_ID")));
        List<String> refOrderFields = new SequenceSet<>();
        refOrderFields.addAll(map.get(Order.class));
        refOrderFields.removeAll(Arrays.asList("MANIFEST_ID"));

        map.put(RefOrder.class,refOrderFields);
        map.put(ShipToAddress.class,ModelReflector.instance(ShipToAddress.class).getVisibleFields());


        ModelReflector<OrderLine> orderLineModelReflector = ModelReflector.instance(OrderLine.class);
        map.put(OrderLine.class,orderLineModelReflector.getVisibleFields(Arrays.asList("ID","LOCK_ID")));
        map.get(OrderLine.class).removeAll(Arrays.asList("ORDER_ID","SHIP_FROM_ID","INVENTORY_ID"));

        ModelReflector<OrderStatus> orderStatusModelReflector = ModelReflector.instance(OrderStatus.class);
        map.put(OrderStatus.class,orderStatusModelReflector.getVisibleFields(Arrays.asList("ID","LOCK_ID")));
        map.get(OrderStatus.class).remove("ORDER_ID");

        ModelReflector<in.succinct.plugins.ecommerce.db.model.inventory.Sku> skuModelReflector = ModelReflector.instance(in.succinct.plugins.ecommerce.db.model.inventory.Sku.class);
        map.put(Sku.class,skuModelReflector.getVisibleFields(Arrays.asList("ID","LOCK_ID")));
        map.put(in.succinct.mandi.db.model.Item.class,ModelReflector.instance(in.succinct.plugins.ecommerce.db.model.catalog.Item.class).getUniqueFields());
        map.get(in.succinct.mandi.db.model.Item.class).add("HUM_BHI_ONLINE_SUBSCRIPTION_ITEM");

        List<String> userFields = new ArrayList<>();
        List<String> facilityFields = new ArrayList<>();

        for (String addressField : Address.getAddressFields()) {
            userFields.add(addressField);
            facilityFields.add(addressField);
        }
        userFields.add("PHONE_NUMBER");
        facilityFields.add("PHONE_NUMBER");


        userFields.addAll(ModelReflector.instance(User.class).getUniqueFields());
        userFields.retainAll(ModelReflector.instance(User.class).getVisibleFields());
        userFields.addAll(Arrays.asList("ID","NAME_AS_IN_BANK_ACCOUNT","VIRTUAL_PAYMENT_ADDRESS"));

        facilityFields.addAll(ModelReflector.instance(Facility.class).getUniqueFields());
        facilityFields.add("ID");
        facilityFields.add("DELIVERY_PROVIDED");
        facilityFields.add("COD_ENABLED");
        facilityFields.add("CREATOR_USER_ID");
        facilityFields.add("MERCHANT_FACILITY_REFERENCE");

        map.put(User.class,userFields);
        map.put(Facility.class,facilityFields);

        return map;

    }

    @COLUMN_DEF(StandardDefault.NULL)
    public String getPreferredCarrierName();

    @PARTICIPANT
    @Index
    public long getFacilityId();
    public void setFacilityId(long facilityId);
    public Facility getFacility();

    @COLUMN_SIZE(2048)
    public String  getUpiResponse();
    public void setUpiResponse(String upiResponse);


    @Override
    @PARTICIPANT
    @Index
    Long getCreatorUserId();


    void completePayment(boolean save);
    void completeRefund(boolean save);


    @COLUMN_DEF(StandardDefault.ZERO)
    double getAmountPaid();
    void setAmountPaid(double amountPaid);

    @COLUMN_DEF(StandardDefault.ZERO)
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
    public void resetPayment(boolean save);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    boolean isRefundInitialized();
    void setRefundInitialized(boolean initialized);

    public void initializeRefund();
    public void resetRefund(boolean save);



    @HIDDEN
    public Long getParentOrderId();
    public void setParentOrderId(Long id);
    public Order getParentOrder();

    @IS_VIRTUAL
    public Long getRefOrderId();
    public void setRefOrderId(Long id);
    @IS_VIRTUAL
    public RefOrder getRefOrder();

    @IS_VIRTUAL
    public Long getShipToAddressId();
    public void setShipToAddressId(Long id);
    @IS_VIRTUAL
    public ShipToAddress getShipToAddress();

    @IS_VIRTUAL
    public Long getBillToAddressId();
    public void setBillToAddressId(Long id);
    @IS_VIRTUAL
    public BillToAddress getBillToAddress();


    @IS_VIRTUAL
    @Index
    public boolean isDeliveryPlanned();

    @IS_VIRTUAL
    @Index
    public boolean isOpen();

    @IS_VIRTUAL
    @Index
    public boolean isCancelled();

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isCustomerPickup();
    public void setCustomerPickup(boolean customerPickup);
    //In case of customer pickup , customer may choose courier, or else the seller may choose courier and bear the cost.

    @IS_VIRTUAL
    public Order getTransportOrder();



    public Double getWeight();
    public void setWeight(Double weight);

    public Long getWeightUomId();
    public void setWeightUomId(Long id);
    public UnitOfMeasure getWeightUom();

    @UNIQUE_KEY(value = "KExternal",allowMultipleRecordsWithNull = true)
    @IS_NULLABLE
    public String getExternalTransactionReference();
    public void setExternalTransactionReference(String externalTransactionReference);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isOnHold();
    public void setOnHold(boolean hold);

    @HIDDEN
    public String getExternalPlatformId();
    public void setExternalPlatformId(String bppId);
    


    public static Order find (String externalTransactionReference){
        List<Order> orders = new Select().from(Order.class).where(new Expression(ModelReflector.instance(Order.class).getPool(), "EXTERNAL_TRANSACTION_REFERENCE", Operator.EQ,externalTransactionReference)).execute();
        if (orders.size() != 1){
            return null;
        }
        return orders.get(0);
    }
}
