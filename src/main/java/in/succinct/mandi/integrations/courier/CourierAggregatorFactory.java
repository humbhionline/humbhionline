package in.succinct.mandi.integrations.courier;

import in.succinct.mandi.db.model.Inventory;

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
       put(Inventory.BECKN,BecknCourierAggregator.getInstance());
    }};

    public CourierAggregator getCourierAggregator(String name){
        CourierAggregator aggregator =  courierAggregatorMap.get(name);
        if (aggregator == null){
            throw new RuntimeException(String.format("Don't know how to integrate with CourierAggregator (%s)!",name));
        }
        return aggregator;
    }
}
