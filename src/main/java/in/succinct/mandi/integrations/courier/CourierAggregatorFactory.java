package in.succinct.mandi.integrations.courier;

import in.succinct.mandi.db.model.beckn.BecknNetwork;

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


    public String getBecknNetworkAggregatorKey(BecknNetwork network){
        return String.format("beckn://%s",network.getNetworkId());
    }


    public CourierAggregator getCourierAggregator(BecknNetwork network){

        String aggregatorKey = getBecknNetworkAggregatorKey(network);
        CourierAggregator aggregator =  courierAggregatorMap.get(aggregatorKey);

        if (aggregator == null){
            throw new RuntimeException(String.format("Don't know how to integrate with CourierAggregator (%s)!", aggregatorKey));
        }
        return aggregator;
    }
}
