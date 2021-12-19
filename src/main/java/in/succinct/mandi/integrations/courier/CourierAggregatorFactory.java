package in.succinct.mandi.integrations.courier;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.sql.Select;
import in.succinct.mandi.db.model.Inventory;
import in.succinct.mandi.db.model.beckn.BecknNetwork;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CourierAggregatorFactory {
    private static volatile CourierAggregatorFactory instance = null;
    private static final Object mutex = new Object();

    public static CourierAggregatorFactory getInstance(){
        if (instance == null){
            synchronized (mutex){
                if (instance == null){
                    instance = new CourierAggregatorFactory();
                }
            }
        }
        return instance;
    }

    private Map<String,CourierAggregator> courierAggregatorMap = new ConcurrentHashMap<String,CourierAggregator>(){{
        for (BecknNetwork network : BecknNetwork.all()) {
            put(getBecknNetworkAggregatorKey(network), new BecknCourierAggregator(network));
        }
    }};

    public String getAggregatorKey(Inventory deliveryRecord){
        String aggregatorKey ;
        if (ObjectUtil.equals(Inventory.BECKN,deliveryRecord.getManagedBy())){
            aggregatorKey = String.format("%s://%s",Inventory.BECKN,deliveryRecord.getBecknNetwork().getRegistryId());
        }else {
            aggregatorKey = deliveryRecord.getManagedBy();
        }
        return aggregatorKey;
    }
    public String getBecknNetworkAggregatorKey(BecknNetwork network){
        return String.format("%s://%s",Inventory.BECKN,network.getRegistryId());
    }


    public CourierAggregator getCourierAggregator(Inventory deliveryRecord){
        if (!deliveryRecord.isCourierAggregator()){
            throw new RuntimeException("Not a courier integrator");
        }
        String aggregatorKey = getAggregatorKey(deliveryRecord);
        CourierAggregator aggregator =  courierAggregatorMap.get(aggregatorKey);

        if (aggregator == null){
            throw new RuntimeException(String.format("Don't know how to integrate with CourierAggregator (%s)!", aggregatorKey));
        }
        return aggregator;
    }
}
