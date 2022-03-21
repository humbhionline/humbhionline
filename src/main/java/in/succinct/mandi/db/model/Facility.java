package in.succinct.mandi.db.model;

import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.ENCRYPTED;
import com.venky.swf.db.annotations.column.HOUSEKEEPING;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.indexing.Index;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.db.annotations.column.ui.PROTECTION;
import in.succinct.plugins.ecommerce.db.model.order.OrderLine;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface Facility extends EncryptedAddress , in.succinct.plugins.ecommerce.db.model.participation.Facility {
    @COLUMN_NAME("ID")
    @PROTECTION
    @HIDDEN
    @HOUSEKEEPING
    @PARTICIPANT(redundant = true)
    public long getSelfFacilityId();
    public void setSelfFacilityId(long id);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    @Index
    public boolean isPublished();
    public void setPublished(boolean published);



    @IS_VIRTUAL
    public Facility getSelfFacility();


    @IS_VIRTUAL
    public Double getDistance();
    public void setDistance(Double distance);

    public String getGSTIN();
    public void setGSTIN(String gstin);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isDeliveryProvided();
    public void setDeliveryProvided(boolean deliveryProvided);

    @COLUMN_DEF(StandardDefault.ZERO)
    public double getDeliveryRadius();
    public void setDeliveryRadius(double deliveryRadius);

    @COLUMN_DEF(StandardDefault.ZERO)
    @COLUMN_NAME("MIN_FIXED_DISTANCE")
    public double getMinChargeableDistance();
    public void setMinChargeableDistance(double fixedDistance);

    @COLUMN_DEF(StandardDefault.ZERO)
    @COLUMN_NAME("FIXED_DELIVERY_CHARGES")
    public double getMinDeliveryCharge();
    public void setMinDeliveryCharge(double charges);


    @COLUMN_DEF(StandardDefault.ZERO)
    public double getChargesPerKm();
    public void setChargesPerKm(double chargesPerKm);

    @IS_VIRTUAL
    public double getDeliveryCharges(double distance);

    @IS_VIRTUAL
    public Double getDeliveryCharges();

    @IS_VIRTUAL
    public Inventory getDeliveryRule(boolean published);

    public void publish();
    public void unpublish();


    @IS_VIRTUAL
    boolean isCurrentlyAtLocation();
    void setCurrentlyAtLocation(boolean currentlyAtLocation);

    @Index
    public Long getCreatorUserId();


    public List<Order> getOrders();

    @COLUMN_DEF(StandardDefault.BOOLEAN_TRUE)
    public boolean isCodEnabled();
    public void setCodEnabled(boolean enabled);


    @IS_VIRTUAL
    public int getNumSkus();


    public String getNotificationUrl();
    public void setNotificationUrl(String baseUrl);

    @ENCRYPTED
    public String getToken();
    public void setToken(String token);

    public static final String EVENT_TYPE_BOOK_ORDER = "book_order";
    public static final String EVENT_TYPE_DELIVERED = "order_delivered";
    public void notifyEvent(String event, Order order);

    public static final String EVENT_TYPE_CANCEL_ORDER_LINE = "order_line_cancelled";
    public void notifyEvent(String event, OrderLine orderLine);

    public String getMerchantFacilityReference();
    public void setMerchantFacilityReference(String merchantFacilityReference);


    @IS_VIRTUAL
    List<User> getOperatingUsers();

    @IS_NULLABLE
    public BigDecimal getMinLat();
    public void setMinLat(BigDecimal lat);

    @IS_NULLABLE
    public BigDecimal getMinLng();
    public void setMinLng(BigDecimal lat);

    @IS_NULLABLE
    public BigDecimal getMaxLat();
    public void setMaxLat(BigDecimal lat);

    @IS_NULLABLE
    public BigDecimal getMaxLng();
    public void setMaxLng(BigDecimal lng);


    @IS_NULLABLE
    public String getCustomDomain();
    public void setCustomDomain(String domain);

    @HIDDEN
    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isCustomDomainApproved();
    public void setCustomDomainApproved(boolean customDomainApproved);

    @IS_VIRTUAL
    public void approveCustomDomain();
}
